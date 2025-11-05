## 编译

带 log4j 的版本：mvn clean package ,  运行：java -Dlog4j.configurationFile=log4j2.xml \
     -jar target/dns-1.0.0-jar-with-dependencies.jar ren.mysql.db.test



不带 log4j 的版本：javac DnsResolver.java  运行：java DnsResolver

## 要求

要求在 Linux 机器上配置一个自定义的域名，比如：ren.mysql.db.test，运行的时候每次都能执行真正的 dns 解析，比如 tcpdump 能抓到包，比如 nameserver 能收到这个解析请求，不能走任何 cache
