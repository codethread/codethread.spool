CLJ_KONDO := clj-kondo

.PHONY: test kondo kondo-import kondo-lint clean-kondo

test:
	clojure -M:test

kondo:
	$(MAKE) kondo-import
	$(MAKE) kondo-lint

kondo-import:
	@echo "==> .millstrand clj-kondo imports"
	@rm -rf .clj-kondo/imports
	@mkdir -p .clj-kondo
	@classpath="$$(clojure -Srepro -Spath)" && \
	$(CLJ_KONDO) --repro --lint "$$classpath" --copy-configs --skip-lint

kondo-lint:
	@echo "==> .millstrand clj-kondo"
	@$(CLJ_KONDO) --repro --parallel --lint init.clj me test

clean-kondo:
	rm -rf .clj-kondo/imports .clj-kondo/.cache
