import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.Security;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class DnsResolverLog4j {

    // 引入 Log4j2 Logger
    private static final Logger logger = LogManager.getLogger(DnsResolverLog4j.class);

    // 默认值
    private static final String DEFAULT_DOMAIN = "ren.mysql.db.test";
    private static final int DEFAULT_LOOP_INTERVAL_MS = 1000;
    
    // JVM 属性键
    private static final String JVM_IPV4_PREFERENCE_KEY = "java.net.preferIPv4Stack";
    private static final String JVM_DNS_TTL_KEY = "networkaddress.cache.ttl";
    private static final String JVM_DNS_NEG_TTL_KEY = "networkaddress.cache.negative.ttl";

    public static void main(String[] args) {
        
        // --- 1. 初始化配置 ---
        String domain = DEFAULT_DOMAIN;
        int ttl = 0; // 默认设置为 0，强制禁用
        
        // --- 解析命令行参数 (简化处理，只接受域名作为第一个参数) ---
        if (args.length > 0) {
            domain = args[0];
        }

        // --- 2. 预先读取 JVM 参数 (如果有) ---
        // 这一步只是为了日志输出，实际设置依赖 Security.setProperty()
        String ttlFromCmd = System.getProperty(JVM_DNS_TTL_KEY);
        if (ttlFromCmd != null) {
            try {
                ttl = Integer.parseInt(ttlFromCmd);
            } catch (NumberFormatException ignored) {
                // 使用默认值 0
            }
        }
        
        // --- 3. 应用 JVM 和 Security 属性 (保留您发现的关键代码) ---
        logger.info("Applying DNS Cache TTL setting: {} seconds (Forced via Security Policy)", ttl);
        
        // 🚀 核心关键：确保这个设置被 InetAddress 的缓存策略读取
        Security.setProperty(JVM_DNS_TTL_KEY, String.valueOf(ttl));
        Security.setProperty(JVM_DNS_NEG_TTL_KEY, String.valueOf(ttl)); 

        String ipv4Preference = System.getProperty(JVM_IPV4_PREFERENCE_KEY, "false"); 
        
        // 强制使用 IPv4，因为它有助于简化 DNS 解析的输出
        if (!"true".equals(ipv4Preference)) {
            System.setProperty(JVM_IPV4_PREFERENCE_KEY, "true");
            ipv4Preference = "true (Forced)";
        }
        
        // --- 4. 打印配置信息 (使用 Log4j2) ---
        logger.info("==================================================");
        logger.info(" Simple DNS Resolver Started (Log4j2 Version)");
        logger.info("==================================================");
        logger.info("Target Domain:       {}", domain);
        logger.info("Interval:            {} ms", DEFAULT_LOOP_INTERVAL_MS);
        // 再次确认最终生效的值
        logger.info("JVM preferIPv4Stack: {}", System.getProperty(JVM_IPV4_PREFERENCE_KEY));
        logger.info("JVM DNS TTL:         {} (Final Value)", Security.getProperty(JVM_DNS_TTL_KEY));
        logger.info("==================================================");

        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
        
        int count = 0;
        String lastResolvedAddress = "N/A";

        while (true) {
            count++;
            long startTime = System.currentTimeMillis();
            logger.info("DNS Cache TTL setting: {}  (Log4j2 Version)", Security.getProperty(JVM_DNS_TTL_KEY));
            try {
                // 核心解析操作：使用 getAllByName 绕开 getByName 缓存
                InetAddress[] addresses = InetAddress.getAllByName(domain);
                
                long endTime = System.currentTimeMillis();
                String resolvedAddress = addresses.length > 0 ? addresses[0].getHostAddress() : "N/A";
                
                if (!resolvedAddress.equals(lastResolvedAddress)) {
                    // IP 变化时，使用 WARN 级别突出显示
                    logger.warn("[{}] #{} | RTT: {} ms | NEW IP: {} -> {}", 
                                LocalDateTime.now().format(dtf), count, (endTime - startTime), 
                                lastResolvedAddress, resolvedAddress);
                    lastResolvedAddress = resolvedAddress;
                } else {
                    logger.info("[{}] #{} | RTT: {} ms | Resolved: {}",
                                LocalDateTime.now().format(dtf), count, (endTime - startTime), resolvedAddress);
                }

            } catch (UnknownHostException e) {
                long endTime = System.currentTimeMillis();
                logger.error("[{}] #{} | RTT: {} ms | ERROR: {}",
                                LocalDateTime.now().format(dtf), count, (endTime - startTime), e.getMessage());
            }

            // 控制循环间隔
            try {
                Thread.sleep(DEFAULT_LOOP_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.info("Application interrupted. Exiting.");
                break;
            }
        }
    }
}
