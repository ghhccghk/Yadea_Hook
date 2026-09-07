# Yadea SmartMoto 登录API分析文档

## 概述

本文档基于对 Yadea SmartMoto 应用 (com.yadea.smartmoto) 反编译代码的分析，详细说明了登录相关的API接口、请求参数、技术实现和安全机制。

## 基础配置

### 环境URL

| 环境 | URL |
|------|-----|
| 生产环境 | https://gw.yadeaiot.com.cn/api/app/ |
| 测试环境 | https://gw.alitest.yadeaiot.com.cn/api/app/ |
| UAT环境 | https://gw.uat.yadeaiot.com.cn/api/app/ |
| 商城生产 | https://mall.yadeaiot.com.cn/ |
| 商城测试 | https://test-mall.yadeaiot.cn/ |

### 网关配置

`
gatewayAppId: 0e7b1d094c7b4073b7c958edbbbc1115
加密密钥: cIDJ9f05d1604703
超时时间: 15000ms (15秒)
`

## 登录API详情

### 1. 密码登录

`
POST /user/loginByPassword
`

**请求参数**:

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| ccount | String | 是 | 手机号、邮箱或用户名 |
| password | String | 是 | 用户密码 |
| ccountType | Integer | 是 | 账号类型 |
| ppType | Integer | 是 | 固定值 1 |
| oppoPushId | String | 否 | OPPO推送ID |
| egistrationId | String | 否 | 推送注册ID (个推/OPPO) |

**请求头**:

| Header | 说明 |
|--------|------|
| device-brand | 设备品牌信息 |
| gatewayAppId | 网关应用ID |
| Content-Type | pplication/json |

**代码位置**: k05.smali → 方法 p(Ljava/lang/String;Ljava/lang/String;I)V

### 2. 短信验证码登录

`
POST /user/loginBySms
`

**请求参数**:

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| mobile | String | 是 | 手机号码 |
| code | String | 是 | 短信验证码 |
| password | String | 否 | 密码 (可选) |
| loginAppType | Integer | 是 | 固定值 1 |
| oppoPushId | String | 否 | OPPO推送ID |
| egistrationId | String | 否 | 推送注册ID |

**代码位置**: k05.smali → 方法 (Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V

### 3. 第三方登录绑定手机号

`
POST /user/third-auth/openid-mobile/bind
`

**请求参数**:

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| mobile | String | 是 | 手机号码 |
| msgCode | String | 是 | 短信验证码 |
| 	hirdLoginType | Integer | 是 | 第三方登录类型 |
| ppNameId | Integer | 是 | 固定值 1 |
| sendCode | Integer | 是 | 固定值 8 |
| openid | String | 否 | 第三方OpenID |
| 	hirdNickname | String | 否 | 第三方昵称 |
| qqUrl | String | 否 | QQ头像URL |
| code | String | 否 | 第三方授权码 |
| egistrationId | String | 否 | 推送注册ID |
| yadeaAppType | String | 否 | 固定值 1 (仅type=0时) |

**代码位置**: k05.smali → 方法 i(Ljava/lang/String;Ljava/lang/String;Lcom/yadea/smartmoto/login/bean/OtherLoginBean;)V

## 其他用户相关API

### 验证码相关

| API | 方法 | 说明 |
|-----|------|------|
| /user/sendCode | POST | 发送短信验证码 |
| /user/sendCodeByEmail | POST | 发送邮箱验证码 |
| /user/sendCode/third-mobile | POST | 第三方登录发送验证码 |

### 密码管理

| API | 方法 | 说明 |
|-----|------|------|
| /user/setPasswordByUserId | POST | 设置密码 |
| /user/updatePasswordByCode | POST | 验证码修改密码 |

### 账号绑定

| API | 方法 | 说明 |
|-----|------|------|
| /user/bindOrChangeMobile | POST | 绑定/更换手机号 |
| /user/bindOrChangeEmail | POST | 绑定/更换邮箱 |

### 注册

| API | 方法 | 说明 |
|-----|------|------|
| /user/registerByEmail | POST | 邮箱注册 |

## 技术实现

### 网络框架

`
├── Retrofit 2.x
│   ├── GsonConverterFactory (JSON解析)
│   ├── MoshiConverterFactory (JSON解析)
│   └── RxJava2CallAdapter (异步处理)
├── OkHttp 3.x
│   ├── CommonParamsInterceptor (公共参数)
│   ├── CommonHeadInterceptor (公共请求头)
│   ├── MallSignatureInterceptor (商城签名)
│   └── 加密拦截器
└── 自定义缓存
    ├── GsonDiskConverter
    └── CacheMode.NO_CACHE
`

### 请求流程

`
1. 构建请求参数 (HashMap)
2. 添加公共参数 (签名、时间戳等)
3. 添加公共请求头 (Token、设备信息)
4. JSON序列化请求体
5. 发送HTTP请求
6. 解析响应JSON
7. 返回UserInfoBean对象
`

### 代码结构

`
com.yadea.smartmoto
├── login/
│   ├── view/
│   │   ├── LoginTypeActivity.smali      # 登录类型选择
│   │   ├── RegisteredActivity.smali     # 注册Activity
│   │   └── BindTypeActivity.smali       # 绑定类型
│   ├── fragment/
│   │   ├── MobileLoginFragment.smali    # 手机号登录
│   │   ├── EmailLoginFragment.smali     # 邮箱登录
│   │   └── MobileRegisteredFragment.smali # 手机注册
│   ├── viewmodel/
│   │   └── RegisteredViewModel.smali    # 注册ViewModel
│   └── bean/
│       └── OtherLoginBean.smali         # 第三方登录Bean
├── network/
│   ├── interceptor/
│   │   ├── CommonParamsInterceptor.smali # 公共参数
│   │   └── CommonHeadInterceptor.smali   # 公共请求头
│   └── request/
│       └── BaseBodyRequest.smali         # 请求基类
└── base/
    └── base/
        └── UserInfoBean.smali            # 用户信息
`

## 安全机制

### 1. 请求签名

所有API请求都经过签名处理，签名算法使用 cIDJ9f05d1604703 作为密钥。

**签名拦截器**: CommonParamsInterceptor

### 2. Token认证

登录成功后返回的Token用于后续请求的身份验证。

**Token存储**: UserInfoBean.token
**过期时间**: UserInfoBean.tokenExpire

### 3. 设备绑定

请求包含设备相关信息：
- device-brand: 设备品牌
- egistrationId: 推送注册ID
- oppoPushId: OPPO推送ID
- ppType: 应用类型

### 4. 传输安全

- 使用HTTPS协议
- 请求头包含网关认证信息
- 敏感数据加密传输

## 数据模型

### UserInfoBean

`java
public class UserInfoBean {
    @PrimaryKey
    public long id;                    // 用户ID
    public String token;               // 认证令牌
    public long tokenExpire;           // 令牌过期时间
    public String userId;              // 用户ID (字符串)
    public String mobile;              // 手机号
    public String email;               // 邮箱
    public String nickName;            // 昵称
    public String userName;            // 用户名
    public String realName;            // 真实姓名
    public String authKey;             // 认证密钥
    public int userType;               // 用户类型
    public int loginAppType;           // 登录应用类型
    public int sex;                    // 性别
    public String birthDate;           // 出生日期
    public String picUrl;              // 头像URL
    public String backImage;           // 背景图
    public String area;                // 地区
    public String location;            // 位置
    public String provinceCity;        // 省市
    public String officialCertification; // 官方认证
    public String remark;              // 备注
    public String createTime;          // 创建时间
    public String updateTime;          // 更新时间
    public List<UserTagBean> tagList;  // 标签列表
}
`

### OtherLoginBean

`java
public class OtherLoginBean {
    public String code;       // 第三方授权码
    public String openID;     // 第三方OpenID
    public String nickName;   // 第三方昵称
    public String qqUrl;      // QQ头像URL
    public int type;          // 第三方登录类型
}
`

## 调试建议

### 1. 抓包分析

使用 Charles/Fiddler 等工具抓包时注意：
- HTTPS证书需要安装到设备
- 关闭SSL Pinning (如有)
- 关注 gatewayAppId 头部

### 2. Hook点

如需Hook登录请求，可关注以下类：
- k05.smali (RegisteredModel) - 所有登录相关请求
- CommonParamsInterceptor - 公共参数添加
- CommonHeadInterceptor - 公共请求头添加

### 3. 日志输出

应用使用 Ly03; 类进行日志输出，Tag为：
- CodeInputModel - 登录相关日志
- CommonParamsInterceptor - 参数日志

## 版本信息

- **分析版本**: 基于反编译代码
- **混淆方式**: ProGuard/R8
- **代码位置**: k05.smali, p4.smali
- **更新日期**: 2024

## 注意事项

1. 所有接口均为POST请求
2. 请求体格式为JSON
3. 响应格式为JSON
4. 需要处理Token过期情况
5. 部分接口需要登录后才能调用
6. 测试环境和生产环境URL不同

---

*本文档基于静态代码分析生成，实际API可能因版本更新而变化。*
