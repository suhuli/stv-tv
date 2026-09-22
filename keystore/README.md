# 签名文件

- `stv-release.jks`：正式签名证书（PKCS12），alias `stv`，密码见 `keystore.properties`
- 已被 `.gitignore` 忽略，**请自行备份**。丢失后新包无法覆盖安装旧包，用户必须卸载重装。
- 如需换成自己的证书，替换 jks 并修改 `keystore.properties` 即可。
