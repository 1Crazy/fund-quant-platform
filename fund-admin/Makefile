.PHONY: dev dev-hot hot-compile install package prepare

SDKMAN_DIR ?= $(HOME)/.sdkman
JAVA_HOME := $(SDKMAN_DIR)/candidates/java/current
MAVEN_HOME := $(SDKMAN_DIR)/candidates/maven/current
MAVEN := $(MAVEN_HOME)/bin/mvn
export JAVA_HOME
export PATH := $(JAVA_HOME)/bin:$(MAVEN_HOME)/bin:$(PATH)
PROFILE := dev
RESTART_FILE ?= ruoyi-admin/target/.hot-restart

# 日常开发启动本地热部署：保存 ruoyi-admin 或 ruoyi-fund 源码后自动增量编译，
# 再受控重启 Java 子进程。RuoYi 的 Redisson 基础设施不兼容 DevTools 类加载器重启。
dev: dev-hot

dev-hot: prepare
	@set -e; \
	restart_file="ruoyi-admin/target/.hot-restart"; \
	printf '%s\n' initial > "$$restart_file"; \
	$(MAKE) --no-print-directory hot-compile RESTART_FILE="$$restart_file" & watcher_pid=$$!; \
	app_pid=""; \
	stop_app() { \
		if [ -n "$$app_pid" ] && kill -0 "$$app_pid" 2>/dev/null; then \
			pkill -TERM -P "$$app_pid" 2>/dev/null || true; \
			kill -TERM "$$app_pid" 2>/dev/null || true; \
			wait "$$app_pid" 2>/dev/null || true; \
		fi; \
	}; \
	cleanup() { stop_app; kill "$$watcher_pid" 2>/dev/null || true; wait "$$watcher_pid" 2>/dev/null || true; }; \
	trap 'cleanup; exit 0' INT TERM; \
	trap cleanup EXIT; \
	while :; do \
		( cd ruoyi-admin && exec $(MAVEN) spring-boot:run -P$(PROFILE),hot-run ) & app_pid=$$!; \
		marker=$$(cat "$$restart_file"); \
		while kill -0 "$$app_pid" 2>/dev/null; do \
			[ "$$(cat "$$restart_file")" != "$$marker" ] && break; \
			sleep 1; \
		done; \
		if [ "$$(cat "$$restart_file")" != "$$marker" ]; then \
			echo "[hot] 编译完成，正在重启本地 Java 服务..."; \
			stop_app; \
			app_pid=""; \
		else \
			if wait "$$app_pid"; then exit_code=0; else exit_code=$$?; fi; \
			app_pid=""; \
			[ "$$exit_code" -eq 0 ] && break; \
			echo "[hot] Java 服务异常退出，1 秒后重试。" >&2; \
			sleep 1; \
		fi; \
	done

# macOS 本机未安装 fswatch/entr 时使用的轻量轮询器；仅监控本次基金后端实际开发边界。
hot-compile:
	@compiled=""; stable=""; \
	while :; do \
		current=$$(find ruoyi-admin/src ruoyi-modules/ruoyi-fund/src -type f \( -name '*.java' -o -name '*.xml' -o -name '*.yml' -o -name '*.yaml' -o -name '*.properties' \) -exec stat -f '%m:%N' {} + | LC_ALL=C sort | cksum); \
		if [ "$$current" = "$$stable" ] && [ "$$current" != "$$compiled" ]; then \
			if [ -z "$$compiled" ]; then \
				compiled="$$current"; \
			else \
				echo "[hot] 检测到后端源码变更，正在增量编译..."; \
				if $(MAVEN) -pl ruoyi-admin -am compile -P$(PROFILE) -DskipTests; then \
					printf '%s\n' "$$current" > "$(RESTART_FILE)"; \
					compiled="$$current"; \
				else \
					echo "[hot] 编译失败；修复后再次保存文件会重试。" >&2; \
				fi; \
			fi; \
		fi; \
		stable="$$current"; \
		sleep 2; \
	done

# 模块安装独立成前置目标，只有源码变化时 Maven 才会重新编译。
prepare:
	$(MAVEN) -pl ruoyi-admin -am install -P$(PROFILE) -DskipTests

# 首次安装或模块 POM 变化后执行；测试由项目验证流程单独运行。
install:
	$(MAVEN) install -P$(PROFILE) -DskipTests

# 仅生成当前后端可执行包，不启动服务。
package:
	$(MAVEN) -pl ruoyi-admin -am package -P$(PROFILE) -DskipTests
