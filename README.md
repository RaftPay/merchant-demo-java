# RaftPay Java 商户接入 Demo

Java 8+ 兼容，零第三方依赖，仅使用 JDK 内置类。

## 快速开始

```bash
# 1. 复制配置文件并填入真实凭证
cp config.example.properties config.properties

# 2. 编译并检查环境
./bootstrap.sh

# 3. 运行示例（调用全部接口）
java Example
```

## 文件说明

| 文件 | 说明 |
|------|------|
| `config.example.properties` | 配置模板，复制为 `config.properties` 使用 |
| `RaftPayCrypto.java` | RSA 加解密工具类，可直接复制到你的项目 |
| `RaftPayClient.java` | API 客户端，封装了 4 个接口调用 |
| `JsonUtil.java` | 极简 JSON 序列化/反序列化工具 |
| `Callback.java` | 回调通知接收端，内置 HTTP 服务器 |
| `Example.java` | 全部接口调用示例 |

## 接口清单

| # | 接口 | 方法 |
|---|------|------|
| 1 | 代收订单创建 | `client.createDeposit(params)` |
| 2 | 代收订单创建（直连） | `client.createDeposit(params)` + `directMode=1` |
| 3 | 代付订单创建 | `client.createPayout(params)` |
| 4 | 订单状态查询 | `client.queryOrderStatus(merchantOrderNo)` |
| 5 | 余额查询 | `client.queryBalance()` |
| 6 | 回调通知处理 | 运行 `java Callback` |

## 集成到你的项目

只需复制 `RaftPayCrypto.java`、`RaftPayClient.java` 和 `JsonUtil.java` 三个文件即可。

```java
RaftPayClient client = new RaftPayClient("商户ID", "商户私钥Base64", "https://api_server.raftpay");

// 创建代收
Map<String, Object> params = new LinkedHashMap<>();
params.put("merchantOrderNo", "YOUR_ORDER_NO");
params.put("amount", "100");
params.put("currency", "PKR");
params.put("notifyUrl", "https://your-domain.com/callback");
Map<String, Object> result = client.createDeposit(params);

// 查询余额
Map<String, Object> balance = client.queryBalance();
```

## 回调配置

启动回调服务:

```bash
java Callback
```

默认监听 `8080` 端口（可在 `config.properties` 中修改 `callbackPort`）。确保外网可访问，并在创建订单时传入该地址作为 `notifyUrl`。

回调重试机制：如未返回 `success`，平台将按 30s、1m、4m、10m、30m、1h、2h、6h、15h、24h 间隔重试，共 10 次。
