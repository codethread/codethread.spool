CLJ_KONDO := clj-kondo
CLJ_KONDO_VERSION := 2026.08.04

.PHONY: quality lint lint-millstrand lint-config lint-ralph \
	kondo-configs kondo-configs-millstrand kondo-configs-config \
	kondo-configs-ralph check-clj-kondo clean-kondo

quality: lint
	./scripts/quality.sh

lint: lint-millstrand lint-config lint-ralph

lint-millstrand: kondo-configs-millstrand
	@echo "==> .millstrand clj-kondo"
	@cd .millstrand && $(CLJ_KONDO) --repro --parallel --lint init.clj me

lint-config: kondo-configs-config
	@echo "==> spools/config clj-kondo"
	@cd spools/config && $(CLJ_KONDO) --repro --parallel --lint src test

lint-ralph: kondo-configs-ralph
	@echo "==> spools/ralph clj-kondo"
	@cd spools/ralph && $(CLJ_KONDO) --repro --parallel --lint src test

kondo-configs: kondo-configs-millstrand kondo-configs-config kondo-configs-ralph

kondo-configs-millstrand: check-clj-kondo
	@echo "==> .millstrand clj-kondo imports"
	@cd .millstrand && \
		rm -rf .clj-kondo/imports && \
		mkdir -p .clj-kondo && \
		classpath="$$(clojure -Spath)" && \
		$(CLJ_KONDO) --repro --lint "$$classpath" --copy-configs --skip-lint

kondo-configs-config: check-clj-kondo
	@echo "==> spools/config clj-kondo imports"
	@cd spools/config && \
		rm -rf .clj-kondo/imports && \
		mkdir -p .clj-kondo && \
		classpath="$$(clojure -Spath -M:test)" && \
		$(CLJ_KONDO) --repro --lint "$$classpath" --copy-configs --skip-lint

kondo-configs-ralph: check-clj-kondo
	@echo "==> spools/ralph clj-kondo imports"
	@cd spools/ralph && \
		rm -rf .clj-kondo/imports && \
		mkdir -p .clj-kondo && \
		classpath="$$(clojure -Spath -M:test)" && \
		$(CLJ_KONDO) --repro --lint "$$classpath" --copy-configs --skip-lint

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
	rm -rf \
		.millstrand/.clj-kondo/imports .millstrand/.clj-kondo/.cache \
		spools/config/.clj-kondo/imports spools/config/.clj-kondo/.cache \
		spools/ralph/.clj-kondo/imports spools/ralph/.clj-kondo/.cache
