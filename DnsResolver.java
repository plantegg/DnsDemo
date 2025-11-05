import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.Security;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class DnsResolver {

    // 默认值和常量
    private static final String DEFAULT_DOMAIN = "ren.mysql.db.test";
    private static final int DEFAULT_INTERVAL_MS = 1000;
    private static final int DEFAULT_THREADS = 1;
    private static final int DEFAULT_TTL = 0; // 默认禁用缓存
    
    // JVM 属性键
    private static final String JVM_IPV4_PREFERENCE_KEY = "java.net.preferIPv4Stack";
    private static final String JVM_DNS_TTL_KEY = "networkaddress.cache.ttl";
    private static final String JVM_DNS_NEG_TTL_KEY = "networkaddress.cache.negative.ttl";
    
    // 用于对比的存储空间
    private static volatile String lastResolvedAddress = "N/A (Initial)";

    // ANSI 颜色代码
    private static final String ANSI_RESET = "\u001B[0m";
    private static final String ANSI_GREEN = "\u001B[32m";
    private static final String ANSI_RED = "\u001B[31m";
    private static final String ANSI_CYAN = "\u001B[36m";

    public static void main(String[] args) {
        
        // --- 1. 默认配置 ---
        String domain = DEFAULT_DOMAIN;
        int threadCount = DEFAULT_THREADS;
        int ttl = DEFAULT_TTL;
        
        // --- 2. 解析命令行参数 ---
        if (args.length > 0) domain = args[0];
        if (args.length > 1) {
            try {
                threadCount = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid thread count: " + args[1]);
                return;
            }
        }
        if (args.length > 2) {
            try {
                ttl = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid TTL value: " + args[2]);
                return;
            }
        }
        
        // --- 3. 应用 JVM 和 Security 属性 ---
        Security.setProperty(JVM_DNS_TTL_KEY, String.valueOf(ttl));
        Security.setProperty(JVM_DNS_NEG_TTL_KEY, String.valueOf(ttl)); 
        
        String ipv4Preference = System.getProperty(JVM_IPV4_PREFERENCE_KEY, "false"); 

        
        // --- 4. 打印配置信息 ---
        System.out.println(ANSI_CYAN + "==================================================" + ANSI_RESET);
        System.out.println(ANSI_CYAN + "         Simple Concurrent DNS Resolver" + ANSI_RESET);
        System.out.println(ANSI_CYAN + "==================================================" + ANSI_RESET);
        System.out.println("Target Domain:       " + domain);
        System.out.println("Concurrency Threads: " + threadCount);
        System.out.println("Interval:            " + DEFAULT_INTERVAL_MS + " ms (Total Rate)");
        System.out.println("JVM DNS TTL:         " + ttl + " seconds (0=Disabled)");
        System.out.println("JVM preferIPv4Stack: " + ipv4Preference);
        System.out.println("--------------------------------------------------");


        // --- 5. 启动并发解析任务 ---
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(threadCount);
        AtomicInteger totalCounter = new AtomicInteger(0);
        
        // 调度多个任务并发执行
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            scheduler.scheduleAtFixedRate(
                // 传递 threadCount
                new DnsLookupTask(threadId, totalCounter, domain, threadCount), 
                0, // 初始延迟
                DEFAULT_INTERVAL_MS / threadCount, // 均匀分散任务以实现总体 1s 间隔
                TimeUnit.MILLISECONDS);
        }
    }

    // 内部类：DNS 查询任务
    private static class DnsLookupTask implements Runnable {
        private final int threadId;
        private final AtomicInteger totalCounter;
        private final String domain;
        private final int threadCount; // <--- 修正: 新增 threadCount 成员
        private static final DateTimeFormatter dtf = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
        private static volatile String lastSuccessfulOutput = "";

        // 修正: 构造函数接收 threadCount
        public DnsLookupTask(int threadId, AtomicInteger totalCounter, String domain, int threadCount) { 
            this.threadId = threadId;
            this.totalCounter = totalCounter;
            this.domain = domain;
            this.threadCount = threadCount; // <--- 修正: 存储 threadCount
        }

        @Override
        public void run() {
            int currentCount = totalCounter.incrementAndGet();
            long startTime = System.currentTimeMillis();
            
            try {
                // 核心解析操作
                InetAddress[] addresses = InetAddress.getAllByName(domain);
                long endTime = System.currentTimeMillis();
                
                String currentAddress = addresses.length > 0 
                                      ? Arrays.stream(addresses).map(InetAddress::getHostAddress).reduce((a, b) -> a + "," + b).orElse("N/A")
                                      : "N/A";
                
                long rtt = endTime - startTime;
                
                // 结果对比和输出
                if (!currentAddress.equals(lastResolvedAddress)) {
                    // 结果不一致，高亮输出，并更新缓存
                    String output = String.format("%s[T%d|#%d] RTT: %d ms | NEW IP: %s -> %s%s",
                                                  ANSI_RED, threadId, currentCount, rtt, lastResolvedAddress, currentAddress, ANSI_RESET);
                    System.out.println(output);
                    lastResolvedAddress = currentAddress;
                    lastSuccessfulOutput = output;
                } else if (currentCount % threadCount == 0) {
                    // 结果一致，每 N 个总计数（即每秒）只输出一次
                    String output = String.format("%s[T%d|#%d] RTT: %d ms | Resolved: %s%s",
                                                    ANSI_GREEN, threadId, currentCount, rtt, currentAddress, ANSI_RESET);
                    lastSuccessfulOutput = output;
                    System.out.println(output);
                }
                
            } catch (UnknownHostException e) {
                // 错误处理，高亮输出
                long endTime = System.currentTimeMillis();
                String output = String.format("%s[T%d|#%d] RTT: %d ms | ERROR: %s%s",
                                              ANSI_RED, threadId, currentCount, (endTime - startTime), e.getMessage(), ANSI_RESET);
                System.out.println(output);
            }
        }
    }
}
