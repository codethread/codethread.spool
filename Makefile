CLJ_KONDO := clj-kondo
CLJ_KONDO_VERSION := 2026.08.04
PROJECT_ROOTS := .millstrand spools/config spools/ralph

.PHONY: quality lint kondo kondo-import kondo-lint kondo-configs \
	lint-millstrand lint-config lint-ralph check-clj-kondo clean-kondo

quality: kondo
	./scripts/quality.sh

lint: kondo

kondo:
	$(MAKE) kondo-import
	$(MAKE) kondo-lint

kondo-import: check-clj-kondo
	@set -e; \
	for root in $(PROJECT_ROOTS); do \
		$(MAKE) -C "$$root" kondo-import; \
	done

kondo-lint: check-clj-kondo
	@set -e; \
	for root in $(PROJECT_ROOTS); do \
		$(MAKE) -C "$$root" kondo-lint; \
	done

kondo-configs: kondo-import

lint-millstrand:
	$(MAKE) -C .millstrand kondo

lint-config:
	$(MAKE) -C spools/config kondo

lint-ralph:
	$(MAKE) -C spools/ralph kondo

check-clj-kondo:
	@command -v $(CLJ_KONDO) >/dev/null 2>&1 || { \
		echo "clj-kondo $(CLJ_KONDO_VERSION) is required" >&2; \
		exit 1; \
	}
	@actual="$$($(CLJ_KONDO) --version)"; \
	expected="clj-kondo v$(CLJ_KONDO_VERSION)"; \
	if [ "$$actual" != "$$expected" ]; then \
		echo "Expected $$expected, found $$actual" >&2; \
		exit 1; \
	fi

clean-kondo:
	@set -e; \
	for root in $(PROJECT_ROOTS); do \
		$(MAKE) -C "$$root" clean-kondo; \
	done
