.PHONY: dev install package prepare

SDKMAN_DIR ?= $(HOME)/.sdkman
JAVA_HOME := $(SDKMAN_DIR)/candidates/java/current
MAVEN_HOME := $(SDKMAN_DIR)/candidates/maven/current
MAVEN := $(MAVEN_HOME)/bin/mvn
export JAVA_HOME
export PATH := $(JAVA_HOME)/bin:$(MAVEN_HOME)/bin:$(PATH)
PROFILE := dev

# 日常开发统一从聚合工程编译并安装当前模块，确保不会加载 ~/.m2 中的旧 ruoyi-fund Jar。
dev: prepare
	cd ruoyi-admin && $(MAVEN) spring-boot:run -P$(PROFILE)

# 模块安装独立成前置目标，只有源码变化时 Maven 才会重新编译。
prepare:
	$(MAVEN) -pl ruoyi-admin -am install -P$(PROFILE) -DskipTests

# 首次安装或模块 POM 变化后执行；测试由项目验证流程单独运行。
install:
	$(MAVEN) install -P$(PROFILE) -DskipTests

# 仅生成当前后端可执行包，不启动服务。
package:
	$(MAVEN) -pl ruoyi-admin -am package -P$(PROFILE) -DskipTests
