.PHONY: dev install package prepare

# 即使当前终端位于 ruoyi-admin 子目录，也统一回到 Maven 聚合工程执行。
# 这样会先重新安装 ruoyi-fund 等业务模块，避免 spring-boot:run 读取 ~/.m2 中的旧 Jar。
dev:
	$(MAKE) -C .. dev

prepare:
	$(MAKE) -C .. prepare

install:
	$(MAKE) -C .. install

package:
	$(MAKE) -C .. package
