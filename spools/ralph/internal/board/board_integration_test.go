//go:build integration

package board

import (
	"bufio"
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
	"runtime"
	"strconv"
	"strings"
	"testing"
	"time"
)

const canonicalMillstrand = "8312ad49d02f0f9f20fa167a8305e86a36f3fcae"

type snapshotResult struct {
	snapshot Snapshot
	err      error
}

type processResult struct {
	output string
	err    error
}

func TestRealRalphBoardReadSurvivesPlannedWeaverReplacement(t *testing.T) {
	source := canonicalSourceRoot(t)
	mill := filepath.Join(source, "bin", "mill")
	strand := filepath.Join(source, "bin", "strand")
	for _, bin := range []string{mill, strand} {
		if _, err := os.Stat(bin); err != nil {
			t.Fatalf("canonical tooling binary %s is required: %v", bin, err)
		}
	}

	stateHome := shortTempDir(t)
	t.Setenv("XDG_STATE_HOME", stateHome)
	t.Setenv("MILLSTRAND_SOURCE", source)

	world := t.TempDir()
	armed := filepath.Join(world, "show-armed")
	showEntered := filepath.Join(world, "show-entered")
	probeEntered := filepath.Join(world, "probe-entered")
	probeRelease := filepath.Join(world, "probe-release")
	showRelease := filepath.Join(world, "show-release")
	calls := filepath.Join(world, "show-calls")
	for key, value := range map[string]string{
		"RALPH_TEST_ARMED":         armed,
		"RALPH_TEST_SHOW_ENTERED":  showEntered,
		"RALPH_TEST_PROBE_ENTERED": probeEntered,
		"RALPH_TEST_PROBE_RELEASE": probeRelease,
		"RALPH_TEST_SHOW_RELEASE":  showRelease,
		"RALPH_TEST_SHOW_CALLS":    calls,
	} {
		t.Setenv(key, value)
	}

	millProcess := startMillDaemon(t, mill, source)
	t.Cleanup(func() {
		// Release every disposable gate before stopping the processes so a test
		// failure cannot leave a Weaver child waiting on a temporary file.
		for _, path := range []string{showRelease, probeRelease} {
			_ = os.WriteFile(path, nil, 0o600)
		}
		_, _ = runBinary(mill, source, "weaver", "stop", "--workspace", world)
		stopProcess(t, millProcess, 10*time.Second)
	})

	if out, err := runBinary(mill, source, "init", "--workspace", world); err != nil {
		t.Fatalf("mill init: %v\n%s", err, out)
	}
	writeDisposableRalphWorld(t, world, source, filepath.Join(world, "show-gate.clj"))
	if out, err := runBinary(mill, source, "weaver", "start", "--workspace", world); err != nil {
		t.Fatalf("weaver start: %v\n%s", err, out)
	}
	epic := addRalphEpic(t, strand, source, world)
	if out, err := runBinary(strand, source, "--workspace", world, "kanban", "label", "add", epic, "ralph"); err != nil {
		status, _ := runBinary(mill, source, "weaver", "status", "--workspace", world)
		var statusObject map[string]any
		_ = json.Unmarshal([]byte(strings.TrimSpace(status)), &statusObject)
		logPath, _ := statusObject["log_path"].(string)
		logTail := ""
		if logPath != "" {
			if data, readErr := os.ReadFile(logPath); readErr == nil {
				logTail = string(data)
			}
		}
		t.Fatalf("label epic for Ralph: %v\n%s\nweaver status: %s\nweaver log: %s", err, out, status, logTail)
	}
	if err := os.WriteFile(armed, nil, 0o600); err != nil {
		t.Fatal(err)
	}

	restartDone := make(chan processResult, 1)
	go func() {
		out, err := runBinary(mill, source, "weaver", "restart", "--workspace", world, "--ready-timeout", "90s")
		restartDone <- processResult{output: out, err: err}
	}()
	// The replacement probe keeps the old generation admitted while it is
	// waiting. This makes the subsequent cutover overlap the blocked Ralph
	// read instead of merely placing two independent reads around a restart.
	if !waitForRestartState(mill, source, world, "probing", 60*time.Second) {
		select {
		case result := <-restartDone:
			t.Fatalf("restart ended before probe admission: %v\n%s", result.err, result.output)
		default:
			t.Fatal("the planned replacement did not enter its probing state")
		}
	}
	if !waitForFile(probeEntered, 60*time.Second) {
		t.Fatal("the replacement probe did not enter the disposable Ralph fixture")
	}

	// Admit the real Ralph read while the old generation is still serving during
	// probing. Releasing the probe below then makes cutover interrupt this
	// in-flight read, so the existing read retry is exercised across the
	// structured weaver/restarted boundary.
	readStrand := strandWithTimeout(t, strand, 90*time.Second)
	client := Client{Bin: readStrand, Workspace: world, Timeout: 90 * time.Second}
	readDone := make(chan snapshotResult, 1)
	go func() {
		snapshot, err := client.Snapshot(context.Background(), epic)
		readDone <- snapshotResult{snapshot: snapshot, err: err}
	}()
	if !waitForFile(showEntered, 10*time.Second) {
		callsSoFar, _ := os.ReadFile(calls)
		t.Fatalf("the real Ralph show read did not enter the old Weaver: calls=%q armed=%t", callsSoFar, fileExists(armed))
	}
	if err := os.WriteFile(probeRelease, nil, 0o600); err != nil {
		t.Fatal(err)
	}

	read := <-readDone
	if read.err != nil {
		status, _ := runBinary(mill, source, "weaver", "status", "--workspace", world)
		probeMarker, _ := os.ReadFile(probeEntered)
		select {
		case result := <-restartDone:
			t.Fatalf("Ralph Snapshot across planned replacement: %v\nrestart=%v\n%s\nstatus=%s\nprobe=%q", read.err, result.err, result.output, status, probeMarker)
		default:
			t.Fatalf("Ralph Snapshot across planned replacement: %v\nrestart still running\nstatus=%s\nprobe=%q", read.err, status, probeMarker)
		}
	}
	if read.snapshot.Epic.ID != epic || read.snapshot.Epic.State != StateActive {
		t.Fatalf("snapshot = %+v, want active epic %s from replacement", read.snapshot, epic)
	}

	restart := <-restartDone
	if restart.err != nil {
		t.Fatalf("planned Weaver replacement: %v\n%s", restart.err, restart.output)
	}
	var status map[string]any
	if err := json.Unmarshal([]byte(strings.TrimSpace(restart.output)), &status); err != nil {
		t.Fatalf("restart output is not JSON: %v\n%s", err, restart.output)
	}
	if status["state"] != "running" || status["generation_id"] == "" {
		t.Fatalf("restart did not admit a replacement generation: %#v", status)
	}

	callLines := strings.Split(strings.TrimSpace(readFile(t, calls)), "\n")
	var epicCalls []string
	for _, line := range callLines {
		if strings.HasSuffix(line, ":"+epic) {
			epicCalls = append(epicCalls, line)
		}
	}
	if len(epicCalls) != 2 {
		t.Fatalf("show calls for epic = %q, want old attempt plus replacement reissue", epicCalls)
	}
	oldPID, oldErr := strconv.Atoi(strings.SplitN(epicCalls[0], ":", 2)[0])
	newPID, newErr := strconv.Atoi(strings.SplitN(epicCalls[1], ":", 2)[0])
	if oldErr != nil || newErr != nil || oldPID == newPID {
		t.Fatalf("show calls were not served by distinct Weaver generations: %q", epicCalls)
	}
}

func canonicalSourceRoot(t *testing.T) string {
	t.Helper()
	if configured := os.Getenv("MILLSTRAND_SOURCE_ROOT"); configured != "" {
		verifyCanonicalSource(t, configured)
		return configured
	}
	_, file, _, ok := runtime.Caller(0)
	if !ok {
		t.Fatal("locate integration test")
	}
	source := filepath.Clean(filepath.Join(filepath.Dir(file), "../../../../../skein-src"))
	verifyCanonicalSource(t, source)
	return source
}

func verifyCanonicalSource(t *testing.T, source string) {
	t.Helper()
	out, err := runBinary("git", source, "-C", source, "rev-parse", "HEAD")
	if err != nil || strings.TrimSpace(out) != canonicalMillstrand {
		t.Fatalf("pinned Millstrand %s is required, got %q: %v", canonicalMillstrand, strings.TrimSpace(out), err)
	}
}

func startMillDaemon(t *testing.T, mill, source string) *exec.Cmd {
	t.Helper()
	cmd := exec.Command(mill, "start")
	cmd.Dir = source
	stdout, err := cmd.StdoutPipe()
	if err != nil {
		t.Fatal(err)
	}
	var stderr bytes.Buffer
	cmd.Stderr = &stderr
	if err := cmd.Start(); err != nil {
		t.Fatalf("start mill: %v", err)
	}
	ready := make(chan string, 1)
	go func() {
		scanner := bufio.NewScanner(stdout)
		var diagnostics string
		readySent := false
		for scanner.Scan() {
			line := scanner.Text()
			// Keep consuming the live daemon stream after readiness; retaining only
			// a bounded tail keeps startup failures diagnosable without allowing a
			// noisy disposable daemon to fill the pipe or the test process.
			diagnostics = appendMillDiagnostic(diagnostics, line)
			if !readySent && strings.Contains(line, "mill listening") {
				ready <- diagnostics
				readySent = true
			}
		}
		if !readySent {
			ready <- diagnostics
		}
	}()
	select {
	case output := <-ready:
		if !strings.Contains(output, "mill listening") {
			stopProcess(t, cmd, 2*time.Second)
			t.Fatalf("mill exited before readiness: stdout=%q stderr=%q", output, stderr.String())
		}
	case <-time.After(10 * time.Second):
		stopProcess(t, cmd, 2*time.Second)
		t.Fatal("mill did not become ready")
	}
	return cmd
}

func appendMillDiagnostic(existing, line string) string {
	const maxDiagnostics = 32 << 10
	existing += line + "\n"
	if len(existing) > maxDiagnostics {
		existing = existing[len(existing)-maxDiagnostics:]
	}
	return existing
}

func stopProcess(t *testing.T, cmd *exec.Cmd, timeout time.Duration) {
	t.Helper()
	if cmd == nil || cmd.Process == nil {
		return
	}
	done := make(chan error, 1)
	go func() { done <- cmd.Wait() }()
	if err := cmd.Process.Signal(os.Interrupt); err != nil {
		return
	}
	select {
	case <-done:
	case <-time.After(timeout):
		_ = cmd.Process.Kill()
		<-done
	}
}

func runBinary(bin, dir string, args ...string) (string, error) {
	cmd := exec.Command(bin, args...)
	cmd.Dir = dir
	var out bytes.Buffer
	cmd.Stdout = &out
	cmd.Stderr = &out
	err := cmd.Run()
	return out.String(), err
}

func strandWithTimeout(t *testing.T, strand string, timeout time.Duration) string {
	t.Helper()
	bin := filepath.Join(t.TempDir(), "strand-with-timeout")
	contents := fmt.Sprintf("#!/bin/sh\nexec %q --timeout %s \"$@\"\n", strand, timeout)
	if err := os.WriteFile(bin, []byte(contents), 0o700); err != nil {
		t.Fatalf("write strand timeout wrapper: %v", err)
	}
	return bin
}

func writeDisposableRalphWorld(t *testing.T, world, source, gateFile string) {
	t.Helper()
	localMillhouse := filepath.Join(filepath.Dir(source), "millhouse.spool")
	deps := fmt.Sprintf(`{:deps {millstrand.spools/batteries {:local/root %q}
	             millhouse.spools/kanban {:local/root %q}}}
`, filepath.Join(source, "spools", "batteries"), filepath.Join(localMillhouse, "spools", "kanban"))
	if err := os.WriteFile(filepath.Join(world, "deps.edn"), []byte(deps), 0o644); err != nil {
		t.Fatalf("write disposable deps.edn: %v", err)
	}
	fixture := `(ns ralph.integration.show-gate
  (:require [millstrand.api.weaver.alpha :as weaver]))

(defn- env [key] (System/getenv key))
(defn- exists? [path] (and path (.exists (java.io.File. path))))
(defn- mark! [path]
  (when path (spit path "entered\n" :append true)))
(defn- wait-for! [path]
  (when-not (exists? path)
    (let [file (java.io.File. path)
          parent (.toPath (.getParentFile file))
          watcher (.newWatchService (java.nio.file.FileSystems/getDefault))]
      (try
        (.register parent watcher
                   (into-array java.nio.file.WatchEvent$Kind
                               [java.nio.file.StandardWatchEventKinds/ENTRY_CREATE
                                java.nio.file.StandardWatchEventKinds/ENTRY_MODIFY]))
        ;; Check again after registration so a release between the initial
        ;; check and registration cannot leave this process waiting forever.
        (loop []
          (when-not (exists? path)
            (let [key (.take watcher)]
              (try
                (.pollEvents key)
                (finally
                  (when-not (.reset key)
                    (throw (ex-info "release directory watch became invalid"
                                    {:path path}))))))
            (recur)))
        (finally
          (.close watcher))))))

;; The probe gate holds the old generation admitted while the planned restart
;; prepares its replacement.
(when (and (env "MILLSTRAND_PROBE_STATE")
           (not (exists? (env "RALPH_TEST_PROBE_RELEASE"))))
  (mark! (env "RALPH_TEST_PROBE_ENTERED"))
  (wait-for! (env "RALPH_TEST_PROBE_RELEASE"))
  (mark! (env "RALPH_TEST_PROBE_ENTERED")))

(defonce original-show (deref #'weaver/show))
(defonce installed? (atom false))
(defonce blocked-pid (atom nil))
(when (compare-and-set! installed? false true)
  (alter-var-root #'weaver/show
    (constantly
     (fn [runtime id]
       (let [calls (env "RALPH_TEST_SHOW_CALLS")
             pid (str (.pid (java.lang.ProcessHandle/current)))
             armed (exists? (env "RALPH_TEST_ARMED"))
             entered (env "RALPH_TEST_SHOW_ENTERED")
             entered-exists (exists? entered)
             block? (or (= pid @blocked-pid)
                        (and armed
                             (nil? @blocked-pid)
                             (not entered-exists)
                             (compare-and-set! blocked-pid nil pid)))]
         (when (and calls armed)
           (spit calls (str pid ":" id "\n") :append true))
         (when block?
           (spit entered pid)
           (wait-for! (env "RALPH_TEST_SHOW_RELEASE")))
         (original-show runtime id))))))
`
	if err := os.WriteFile(gateFile, []byte(fixture), 0o644); err != nil {
		t.Fatalf("write show gate: %v", err)
	}
	initPath := filepath.Join(world, "init.clj")
	initContents := fmt.Sprintf(`(load-string %s)

(require '[millstrand.api.current.alpha :as current]
         '[millstrand.api.runtime.alpha :as runtime-api])
(def runtime (current/runtime))

 (runtime-api/module! runtime :millstrand/spools-batteries
                 {:ns 'millstrand.spools.batteries
                  :required? true})
(runtime-api/module! runtime :millhouse/spools-kanban
                 {:ns 'millhouse.spools.kanban
                  :required? true})
`, clojureString(fixture))
	if err := os.WriteFile(initPath, []byte(initContents), 0o644); err != nil {
		t.Fatalf("write disposable init.clj: %v", err)
	}
}

func clojureString(value string) string {
	return strconv.Quote(value)
}

func addRalphEpic(t *testing.T, strand, source, world string) string {
	t.Helper()
	out, err := runBinary(strand, source, "--workspace", world, "kanban", "add",
		"Ralph replacement acceptance", "--type", "epic", "--priority", "p2",
		"--body", "Disposable epic for Ralph read replacement acceptance.")
	if err != nil {
		t.Fatalf("add Ralph epic: %v\n%s", err, out)
	}
	var result struct {
		Card struct {
			ID string `json:"id"`
		} `json:"card"`
	}
	if err := json.Unmarshal([]byte(strings.TrimSpace(out)), &result); err != nil {
		t.Fatalf("add epic output is not JSON: %v\n%s", err, out)
	}
	if result.Card.ID == "" {
		t.Fatalf("add epic returned no card id: %s", out)
	}
	return result.Card.ID
}

func waitForFile(path string, timeout time.Duration) bool {
	deadline := time.NewTimer(timeout)
	defer deadline.Stop()
	tick := time.NewTicker(2 * time.Millisecond)
	defer tick.Stop()
	for {
		if _, err := os.Stat(path); err == nil {
			return true
		}
		select {
		case <-deadline.C:
			return false
		case <-tick.C:
		}
	}
}

func waitForRestartState(mill, source, world, want string, timeout time.Duration) bool {
	deadline := time.NewTimer(timeout)
	defer deadline.Stop()
	tick := time.NewTicker(10 * time.Millisecond)
	defer tick.Stop()
	for {
		out, err := runBinary(mill, source, "weaver", "status", "--workspace", world)
		if err == nil {
			var status map[string]any
			if json.Unmarshal([]byte(strings.TrimSpace(out)), &status) == nil && status["restart_state"] == want {
				return true
			}
		}
		select {
		case <-deadline.C:
			return false
		case <-tick.C:
		}
	}
}

func readFile(t *testing.T, path string) string {
	t.Helper()
	data, err := os.ReadFile(path)
	if err != nil {
		t.Fatalf("read %s: %v", path, err)
	}
	return string(data)
}

func fileExists(path string) bool {
	_, err := os.Stat(path)
	return err == nil
}

func shortTempDir(t *testing.T) string {
	t.Helper()
	dir, err := os.MkdirTemp("/tmp", "ralph-board-")
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { _ = os.RemoveAll(dir) })
	return dir
}
