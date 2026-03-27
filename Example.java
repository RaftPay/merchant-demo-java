import java.io.FileInputStream;
import java.util.*;

/**
 * RaftPay 全部接口调用示例
 *
 * 使用方法:
 *   1. 复制 config.example.properties 为 config.properties 并填入真实凭证
 *   2. ./bootstrap.sh
 *   3. java Example
 */
public class Example {

    public static void main(String[] args) {
        // ============================================================
        // 加载配置
        // ============================================================
        Properties config = new Properties();
        try {
            config.load(new FileInputStream("config.properties"));
        } catch (Exception e) {
            System.err.println("无法加载 config.properties，请先从 config.example.properties 复制并填入真实凭证");
            System.exit(1);
        }

        RaftPayClient client = new RaftPayClient(
                config.getProperty("merchantId"),
                config.getProperty("merchantPrivateKey"),
                config.getProperty("apiBaseUrl")
        );

        String notifyUrl = config.getProperty("notifyUrl", "");
        String returnUrl = config.getProperty("returnUrl", "");

        Map<Integer, String> statusMap = new HashMap<>();
        statusMap.put(0, "待支付");
        statusMap.put(1, "处理中");
        statusMap.put(2, "成功");
        statusMap.put(3, "失败");

        // ============================================================
        // 1. 查询余额
        // ============================================================
        System.out.println("========== 1. 查询余额 ==========");
        try {
            Map<String, Object> result = client.queryBalance();
            if (Integer.valueOf(0).equals(toInt(result.get("result")))) {
                @SuppressWarnings("unchecked")
                Map<String, Object> data = (Map<String, Object>) result.get("data");
                System.out.println("可用余额: " + data.get("availableMoney") + " PKR");
                System.out.println("冻结金额: " + data.get("frozenMoney") + " PKR");
                System.out.println("未结算:   " + data.get("unsettledMoney") + " PKR");
            } else {
                System.out.println("查询失败: " + result.get("message"));
            }
        } catch (Exception e) {
            System.out.println("异常: " + e.getMessage());
        }
        System.out.println();

        // ============================================================
        // 2. 创建代收订单
        // ============================================================
        System.out.println("========== 2. 创建代收订单 ==========");
        String depositOrderNo = "DEP" + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 8);
        try {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("merchantOrderNo", depositOrderNo);
            params.put("amount", "100");
            params.put("currency", "PKR");
            params.put("payType", "JAZZCASH");
            params.put("notifyUrl", notifyUrl);
            params.put("returnUrl", returnUrl);
            params.put("description", "Test deposit");
            params.put("payerMobile", "03001234567");
            params.put("payerEmail", "test@example.com");
            params.put("payerName", "Test User");
            params.put("customerIp", "1.2.3.4");

            Map<String, Object> result = client.createDeposit(params);
            if (Integer.valueOf(0).equals(toInt(result.get("result")))) {
                @SuppressWarnings("unchecked")
                Map<String, Object> data = (Map<String, Object>) result.get("data");
                System.out.println("订单创建成功!");
                System.out.println("平台订单号: " + data.get("orderId"));
                System.out.println("商户订单号: " + data.get("merchantOrderNo"));
                System.out.println("收银台链接: " + data.get("payUrl"));
                int status = toInt(data.get("status"));
                String statusText = statusMap.getOrDefault(status, "未知");
                System.out.println("订单状态:   " + status + " (" + statusText + ")");
            } else {
                System.out.println("创建失败: " + result.get("message") + " (code: " + result.get("result") + ")");
            }
        } catch (Exception e) {
            System.out.println("异常: " + e.getMessage());
        }
        System.out.println();

        // ============================================================
        // 2b. 创建代收订单（直连模式）
        // ============================================================
        System.out.println("========== 2b. 创建代收订单（直连模式） ==========");
        String depositDirectOrderNo = "DEP" + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 8) + "D";
        try {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("merchantOrderNo", depositDirectOrderNo);
            params.put("amount", "100");
            params.put("currency", "PKR");
            params.put("payType", "JAZZCASH");
            params.put("notifyUrl", notifyUrl);
            params.put("returnUrl", returnUrl);
            params.put("description", "Test deposit - direct mode");
            params.put("payerMobile", "03001234567");
            params.put("payerEmail", "test@example.com");
            params.put("payerName", "Test User");
            params.put("customerIp", "1.2.3.4");
            params.put("directMode", 1);

            Map<String, Object> result = client.createDeposit(params);
            if (Integer.valueOf(0).equals(toInt(result.get("result")))) {
                @SuppressWarnings("unchecked")
                Map<String, Object> data = (Map<String, Object>) result.get("data");
                System.out.println("订单创建成功! (直连模式，无收银台链接)");
                System.out.println("平台订单号: " + data.get("orderId"));
                System.out.println("商户订单号: " + data.get("merchantOrderNo"));
                int status = toInt(data.get("status"));
                String statusText = statusMap.getOrDefault(status, "未知");
                System.out.println("订单状态:   " + status + " (" + statusText + ")");
            } else {
                System.out.println("创建失败: " + result.get("message") + " (code: " + result.get("result") + ")");
            }
        } catch (Exception e) {
            System.out.println("异常: " + e.getMessage());
        }
        System.out.println();

        // ============================================================
        // 4. 创建代付订单（手机钱包 MWALLET）
        // ============================================================
        System.out.println("========== 4. 创建代付订单 (MWALLET) ==========");
        String payoutOrderNo = "WDR" + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 8);
        try {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("merchantOrderNo", payoutOrderNo);
            params.put("amount", "100");
            params.put("currency", "PKR");
            params.put("payoutMethod", "MWALLET");
            params.put("payType", "JAZZCASH");
            params.put("payerMobile", "03001234567");
            params.put("accountNumber", "03001234567");
            params.put("accountName", "Test User");
            params.put("notifyUrl", notifyUrl);
            params.put("description", "Test payout - wallet");
            params.put("customerIp", "1.2.3.4");

            Map<String, Object> result = client.createPayout(params);
            if (Integer.valueOf(0).equals(toInt(result.get("result")))) {
                @SuppressWarnings("unchecked")
                Map<String, Object> data = (Map<String, Object>) result.get("data");
                System.out.println("代付订单创建成功!");
                System.out.println("平台订单号: " + data.get("orderId"));
                int status = toInt(data.get("status"));
                String statusText = statusMap.getOrDefault(status, "未知");
                System.out.println("订单状态:   " + status + " (" + statusText + ")");
            } else {
                System.out.println("创建失败: " + result.get("message") + " (code: " + result.get("result") + ")");
            }
        } catch (Exception e) {
            System.out.println("异常: " + e.getMessage());
        }
        System.out.println();

        // ============================================================
        // 5. 创建代付订单（银行转账 IBFT）
        // ============================================================
        System.out.println("========== 5. 创建代付订单 (IBFT) ==========");
        String ibftOrderNo = "WDR" + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 8) + "B";
        try {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("merchantOrderNo", ibftOrderNo);
            params.put("amount", "500");
            params.put("currency", "PKR");
            params.put("payoutMethod", "IBFT");
            params.put("payerMobile", "03001234567");
            params.put("accountNumber", "1234567890123");
            params.put("accountName", "Test User");
            params.put("bankCode", "HBL");
            params.put("notifyUrl", notifyUrl);
            params.put("description", "Test payout - bank transfer");
            params.put("customerIp", "1.2.3.4");

            Map<String, Object> result = client.createPayout(params);
            if (Integer.valueOf(0).equals(toInt(result.get("result")))) {
                @SuppressWarnings("unchecked")
                Map<String, Object> data = (Map<String, Object>) result.get("data");
                System.out.println("代付订单创建成功!");
                System.out.println("平台订单号: " + data.get("orderId"));
                int status = toInt(data.get("status"));
                String statusText = statusMap.getOrDefault(status, "未知");
                System.out.println("订单状态:   " + status + " (" + statusText + ")");
            } else {
                System.out.println("创建失败: " + result.get("message") + " (code: " + result.get("result") + ")");
            }
        } catch (Exception e) {
            System.out.println("异常: " + e.getMessage());
        }
        System.out.println();

        // ============================================================
        // 6. 查询订单状态
        // ============================================================
        System.out.println("========== 6. 查询订单状态 ==========");
        try {
            Map<String, Object> result = client.queryOrderStatus(depositOrderNo);
            if (Integer.valueOf(0).equals(toInt(result.get("result")))) {
                @SuppressWarnings("unchecked")
                Map<String, Object> data = (Map<String, Object>) result.get("data");
                int status = toInt(data.get("status"));
                String statusText = statusMap.getOrDefault(status, "未知");
                System.out.println("商户订单号: " + data.get("merchantOrderNo"));
                System.out.println("平台订单号: " + data.get("orderNo"));
                System.out.println("订单状态:   " + status + " (" + statusText + ")");
                System.out.println("订单金额:   " + data.get("amount") + " " + data.get("currency"));
            } else {
                System.out.println("查询失败: " + result.get("message") + " (code: " + result.get("result") + ")");
            }
        } catch (Exception e) {
            System.out.println("异常: " + e.getMessage());
        }
        System.out.println();

        System.out.println("========== 完成 ==========");
        System.out.println("回调处理请参考 Callback.java");
    }

    /**
     * 安全地将 Object 转为 int（兼容 Integer/Long/Double/String）
     */
    private static int toInt(Object obj) {
        if (obj == null) return -1;
        if (obj instanceof Number) return ((Number) obj).intValue();
        try {
            return Integer.parseInt(obj.toString());
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
