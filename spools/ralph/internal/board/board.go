// Package board reads epic and kanban state through the consumer's strand CLI.
//
// Everything here is read-only. Ralph never mutates the board; the agent runs
// it drives do that themselves. Payloads that do not match the shapes strand
// documents are refused rather than defaulted (TEN-003).
package board

import (
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"os"
	"os/exec"
	"sort"
	"strconv"
	"strings"
	"time"
)

// Attribute keys the kanban spool owns; ralph only ever reads them.
const (
	AttrType     = "kanban/type"
	AttrCard     = "kanban/card"
	AttrLane     = "kanban/lane"
	AttrPriority = "kanban/priority"
	AttrOwner    = "owner"
	AttrBranch   = "branch"
	AttrRalph    = "kanban.label/ralph"
)

// Strand lifecycle states. Anything else is a payload ralph refuses to act on.
const (
	StateActive   = "active"
	StateClosed   = "closed"
	StateReplaced = "replaced"
)

// ErrGate marks every refusal to prompt a model: a missing epic, the wrong card
// type, or a withdrawn ralph label. The loop treats it as a clean stop rather
// than a crash.
var ErrGate = errors.New("epic gate")

// Client invokes a strand binary and decodes its JSON.
type Client struct {
	// Bin is the explicitly selected strand executable resolved from a path or
	// the consumer's PATH before constructing the client.
	Bin string
	// Workspace selects a non-default world, passed through as --workspace.
	Workspace string
	// Timeout bounds a single strand call.
	Timeout time.Duration
}

// CommandError is a structured failure returned by the strand command. The
// command emits this envelope only when it exits unsuccessfully; keeping the
// fields typed lets callers make the one recovery decision Ralph owns without
// scraping human-readable text.
type CommandError struct {
	Args    []string
	Type    string
	Code    string
	Message string
	Details map[string]any
	Err     error
}

func (e *CommandError) Error() string {
	if e == nil {
		return "strand command failed"
	}
	command := strings.Join(e.Args, " ")
	text := ""
	if e.Message != "" {
		text = fmt.Sprintf("strand %s: %s", command, e.Message)
	} else if e.Err != nil {
		text = fmt.Sprintf("strand %s: %v", command, e.Err)
	} else {
		text = fmt.Sprintf("strand %s failed", command)
	}
	diagnostics := []string{}
	if e.Type != "" {
		diagnostics = append(diagnostics, "type="+e.Type)
	}
	if e.Code != "" {
		diagnostics = append(diagnostics, "code="+e.Code)
	}
	if len(e.Details) > 0 {
		details, err := json.Marshal(e.Details)
		if err != nil {
			diagnostics = append(diagnostics, fmt.Sprintf("details=%v", e.Details))
		} else {
			diagnostics = append(diagnostics, "details="+string(details))
		}
	}
	if len(diagnostics) > 0 {
		text += " (" + strings.Join(diagnostics, " ") + ")"
	}
	return text
}

func (e *CommandError) Unwrap() error { return e.Err }

// Strand is the lean projection every strand read returns.
type Strand struct {
	ID         string                     `json:"id"`
	Title      string                     `json:"title"`
	State      string                     `json:"state"`
	CreatedAt  string                     `json:"created_at"`
	UpdatedAt  string                     `json:"updated_at"`
	Attributes map[string]json.RawMessage `json:"attributes"`
}

// Attr returns a scalar attribute as a string. Structured values (an omitted
// body, a nested map) read as empty: callers want them for display only.
func (s Strand) Attr(key string) string {
	value, _ := s.attr(key)
	return value
}

// attr also reports whether the key is present but holds something other than a
// scalar. The gate needs that distinction: an attribute it cannot read is a
// different problem from one nobody set.
func (s Strand) attr(key string) (value string, malformed bool) {
	raw, ok := s.Attributes[key]
	if !ok {
		return "", false
	}
	got, isScalar := scalar(raw)
	return got, !isScalar
}

// Labels returns the card's kanban.label/* keys, sorted.
func (s Strand) Labels() []string {
	var out []string
	for key, raw := range s.Attributes {
		name, ok := strings.CutPrefix(key, "kanban.label/")
		if value, _ := scalar(raw); ok && value == "true" {
			out = append(out, name)
		}
	}
	sort.Strings(out)
	return out
}

// scalar renders a JSON scalar for display, reporting false for anything that
// is not one: a nested map, a list, or an unreadable value.
func scalar(raw json.RawMessage) (string, bool) {
	var v any
	if err := json.Unmarshal(raw, &v); err != nil {
		return "", false
	}
	switch t := v.(type) {
	case string:
		return t, true
	case bool:
		return strconv.FormatBool(t), true
	case float64:
		return strconv.FormatFloat(t, 'f', -1, 64), true
	default:
		return "", false
	}
}

// Task is one slice of a feature card, carrying the status kanban derives from
// its dependency edges.
type Task struct {
	ID     string `json:"id"`
	Title  string `json:"title"`
	Status string `json:"status"`
	State  string `json:"state"`
}

// Card is a feature or epic card as the board projects it.
type Card struct {
	ID       string
	Title    string
	Type     string
	Lane     string
	Priority string
	Owner    string
	Branch   string
	Epic     string
	State    string
	Labels   []string
	// Tasks and Ready are filled for cards under active work only; listing
	// them for every pending card would cost one strand call each.
	Tasks []Task
	Ready []Strand
}

// Snapshot is one poll of everything the UI shows about an epic.
type Snapshot struct {
	Epic     Strand
	Features []Card
	TakenAt  time.Time
}

// lanes orders the board's card groups the way the UI reads them: work in
// flight first, then the queue.
var lanes = []string{"claimed", "in_review", "pending", "refinement"}

type boardPayload struct {
	Claimed    []boardCard `json:"claimed"`
	InReview   []boardCard `json:"in_review"`
	Pending    []boardCard `json:"pending"`
	Refinement []boardCard `json:"refinement"`
}

type boardCard struct {
	ID       string   `json:"id"`
	Title    string   `json:"title"`
	Type     string   `json:"type"`
	Lane     string   `json:"lane"`
	Priority string   `json:"priority"`
	Owner    string   `json:"owner"`
	Branch   string   `json:"branch"`
	Epic     string   `json:"epic"`
	State    string   `json:"state"`
	Labels   []string `json:"labels"`
}

type cardPayload struct {
	Card  Strand   `json:"card"`
	Tasks []Task   `json:"tasks"`
	Ready []Strand `json:"ready"`
}

func (c Client) exec(ctx context.Context, args ...string) ([]byte, error) {
	timeout := c.Timeout
	if timeout <= 0 {
		timeout = 30 * time.Second
	}
	ctx, cancel := context.WithTimeout(ctx, timeout)
	defer cancel()
	return c.run(ctx, args...)
}

func (c Client) run(ctx context.Context, args ...string) ([]byte, error) {
	full := args
	if c.Workspace != "" {
		full = append([]string{"--workspace", c.Workspace}, args...)
	}
	cmd := exec.CommandContext(ctx, c.Bin, full...)
	cmd.Env = jsonErrorEnvironment()
	var stdout, stderr bytes.Buffer
	cmd.Stdout = &stdout
	cmd.Stderr = &stderr
	if err := cmd.Run(); err != nil {
		if ctxErr := ctx.Err(); ctxErr != nil {
			return nil, fmt.Errorf("strand %s: %w", strings.Join(args, " "), ctxErr)
		}
		return nil, parseCommandError(args, stderr.Bytes(), err)
	}
	return stdout.Bytes(), nil
}

// read gives a read-only command one deadline across its initial attempt and
// the sole reissue allowed after a planned Weaver replacement. Mutation-capable
// commands must call exec directly and therefore never inherit this policy.
func (c Client) read(ctx context.Context, args ...string) ([]byte, error) {
	timeout := c.Timeout
	if timeout <= 0 {
		timeout = 30 * time.Second
	}
	ctx, cancel := context.WithTimeout(ctx, timeout)
	defer cancel()

	out, err := c.run(ctx, args...)
	if err == nil || !restarted(err) {
		return out, err
	}
	return c.run(ctx, args...)
}

func restarted(err error) bool {
	var commandErr *CommandError
	return errors.As(err, &commandErr) && commandErr.Code == "weaver/restarted"
}

func jsonErrorEnvironment() []string {
	env := os.Environ()
	filtered := env[:0]
	for _, value := range env {
		if !strings.HasPrefix(value, "MILLSTRAND_ERROR_FORMAT=") {
			filtered = append(filtered, value)
		}
	}
	return append(filtered, "MILLSTRAND_ERROR_FORMAT=json")
}

func parseCommandError(args []string, stderr []byte, cause error) error {
	var envelope struct {
		Type    string         `json:"type"`
		Code    string         `json:"code"`
		Message string         `json:"message"`
		Details map[string]any `json:"details"`
	}
	trimmed := bytes.TrimSpace(stderr)
	if err := json.Unmarshal(trimmed, &envelope); err != nil {
		return &CommandError{
			Args: args,
			Err:  joinCommandCause(cause, fmt.Errorf("malformed strand error envelope: %w (stderr %q)", err, string(trimmed))),
		}
	}
	if envelope.Type == "" || envelope.Code == "" || envelope.Message == "" || envelope.Details == nil {
		return &CommandError{
			Args: args,
			Err:  joinCommandCause(cause, fmt.Errorf("malformed strand error envelope: required fields are missing (stderr %q)", string(trimmed))),
		}
	}
	return &CommandError{
		Args:    args,
		Type:    envelope.Type,
		Code:    envelope.Code,
		Message: envelope.Message,
		Details: envelope.Details,
		Err:     cause,
	}
}

func joinCommandCause(cause, diagnostic error) error {
	if cause == nil {
		return diagnostic
	}
	return errors.Join(cause, diagnostic)
}

// Show reads one strand.
func (c Client) Show(ctx context.Context, id string) (Strand, error) {
	out, err := c.read(ctx, "show", id)
	if err != nil {
		return Strand{}, err
	}
	var s Strand
	if err := json.Unmarshal(out, &s); err != nil {
		return Strand{}, fmt.Errorf("strand show %s returned undecodable JSON: %w", id, err)
	}
	if s.ID != id {
		return Strand{}, fmt.Errorf("strand show %s returned strand %q", id, s.ID)
	}
	if s.Title == "" || s.State == "" {
		return Strand{}, fmt.Errorf("strand show %s returned a strand with no title or state", id)
	}
	return s, nil
}

// Gate reads the epic and refuses anything ralph must not drive: a card that is
// not an epic, or one whose ralph label has been withdrawn. Both scripts ran
// this check before every model prompt so that removing the label stops the
// loop; the binary keeps that contract.
func (c Client) Gate(ctx context.Context, id string) (Strand, error) {
	s, err := c.Show(ctx, id)
	if err != nil {
		return Strand{}, fmt.Errorf("%w: cannot read epic %s: %w", ErrGate, id, err)
	}
	if got, malformed := s.attr(AttrType); got != "epic" {
		return Strand{}, fmt.Errorf("%w: %s has %s=%s, expected epic", ErrGate, id, AttrType, unreadable(got, malformed))
	}
	if got, malformed := s.attr(AttrRalph); got != "true" {
		return Strand{}, fmt.Errorf("%w: %s has %s=%s, expected true", ErrGate, id, AttrRalph, unreadable(got, malformed))
	}
	switch s.State {
	case StateActive, StateClosed, StateReplaced:
	default:
		return Strand{}, fmt.Errorf("%w: %s has unexpected state %q", ErrGate, id, s.State)
	}
	return s, nil
}

// unreadable names why an attribute failed the gate, so a value the CLI could
// not render is not reported as one nobody set.
func unreadable(v string, malformed bool) string {
	switch {
	case malformed:
		return "<malformed>"
	case v == "":
		return "<missing>"
	default:
		return v
	}
}

// Snapshot polls the epic and the feature cards beneath it. Cards under active
// work also carry their tasks and ready frontier, which is what a watcher needs
// to see where the agent actually is.
func (c Client) Snapshot(ctx context.Context, epicID string) (Snapshot, error) {
	epic, err := c.Gate(ctx, epicID)
	if err != nil {
		return Snapshot{}, err
	}
	out, err := c.read(ctx, "kanban", "board")
	if err != nil {
		return Snapshot{}, err
	}
	var payload boardPayload
	if err := json.Unmarshal(out, &payload); err != nil {
		return Snapshot{}, fmt.Errorf("kanban board returned undecodable JSON: %w", err)
	}

	grouped := map[string][]boardCard{
		"claimed":    payload.Claimed,
		"in_review":  payload.InReview,
		"pending":    payload.Pending,
		"refinement": payload.Refinement,
	}
	snap := Snapshot{Epic: epic, TakenAt: time.Now()}
	for _, lane := range lanes {
		for _, raw := range grouped[lane] {
			if raw.Epic != epicID {
				continue
			}
			if raw.ID == "" {
				return Snapshot{}, fmt.Errorf("kanban board returned a %s card under epic %s with no id", lane, epicID)
			}
			card := Card{
				ID: raw.ID, Title: raw.Title, Type: raw.Type, Lane: lane,
				Priority: raw.Priority, Owner: raw.Owner, Branch: raw.Branch,
				Epic: raw.Epic, State: raw.State, Labels: raw.Labels,
			}
			// Tasks are the resume signal, and only work in flight has any
			// worth reading; fetching them for the whole queue would cost a
			// strand call per pending card on every poll.
			if lane == "claimed" || lane == "in_review" {
				detail, err := c.CardDetail(ctx, raw.ID)
				if err != nil {
					return Snapshot{}, err
				}
				card.Tasks = detail.Tasks
				card.Ready = detail.Ready
			}
			snap.Features = append(snap.Features, card)
		}
	}
	return snap, nil
}

// CardDetail reads one card's resume view.
func (c Client) CardDetail(ctx context.Context, id string) (Card, error) {
	out, err := c.read(ctx, "kanban", "card", id)
	if err != nil {
		return Card{}, err
	}
	var payload cardPayload
	if err := json.Unmarshal(out, &payload); err != nil {
		return Card{}, fmt.Errorf("kanban card %s returned undecodable JSON: %w", id, err)
	}
	if payload.Card.ID != id {
		return Card{}, fmt.Errorf("kanban card %s returned card %q", id, payload.Card.ID)
	}
	return Card{
		ID:       payload.Card.ID,
		Title:    payload.Card.Title,
		Type:     payload.Card.Attr(AttrType),
		Lane:     payload.Card.Attr(AttrLane),
		Priority: payload.Card.Attr(AttrPriority),
		Owner:    payload.Card.Attr(AttrOwner),
		Branch:   payload.Card.Attr(AttrBranch),
		State:    payload.Card.State,
		Labels:   payload.Card.Labels(),
		Tasks:    payload.Tasks,
		Ready:    payload.Ready,
	}, nil
}
