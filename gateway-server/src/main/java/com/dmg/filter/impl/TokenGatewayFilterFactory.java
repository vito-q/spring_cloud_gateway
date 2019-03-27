package com.dmg.filter.impl;

import com.dmg.Constants.TokenConstant;
import com.dmg.filter.utlis.TokenStringUtils;
import com.dmg.model.UserModel;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.netty.buffer.ByteBufAllocator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.cloud.gateway.support.BodyInserterContext;
import org.springframework.cloud.gateway.support.CachedBodyOutputMessage;
import org.springframework.cloud.gateway.support.DefaultServerRequest;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.NettyDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserter;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

@Component
public class TokenGatewayFilterFactory extends AbstractGatewayFilterFactory<TokenGatewayFilterFactory.Config> {

    private static final Logger logger = LoggerFactory.getLogger(TokenGatewayFilterFactory.class);

    private static final String CACHE_REQUEST_BODY_OBJECT_KEY = "cachedRequestBodyObject";

    public TokenGatewayFilterFactory() {
        super(Config.class);
    }

    private UserModel userModel;

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            String methodValue = exchange.getRequest().getMethodValue();
            if ("GET".equals(methodValue))
                return erorr(exchange, -1, "非法请求");
            System.err.println("TOKEN网关拦截器生效");
            return apply0(exchange, chain);
        };
    }

    private Mono<Void> apply0(ServerWebExchange exchange, GatewayFilterChain chain) {

        Class inClass = String.class;

        ServerRequest serverRequest = new DefaultServerRequest(exchange);

        ServerHttpRequest request = exchange.getRequest();

        String contentType = request.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE);

        HttpMethod method = serverRequest.method();

        //post请求时，如果是文件上传之类的请求，不修改请求消息体
        if (method == HttpMethod.POST && (MediaType.APPLICATION_FORM_URLENCODED_VALUE.equalsIgnoreCase(contentType)
                || MediaType.APPLICATION_JSON_VALUE.equalsIgnoreCase(contentType))) {

            //从请求里获取Post请求体
            String bodyStr = resolveBodyFromRequest(serverRequest.bodyToFlux(DataBuffer.class));

            //记录日志
            logger.info("全局参数处理: {} url：{} 参数：{}", method.toString(), serverRequest.uri().getRawPath(), bodyStr);

            JsonObject json = null;

            //将数据转为JSON对象
            try {
                json = TokenStringUtils.x_www_form_urlencodedToJson(bodyStr);
            } catch (Exception e) {
                json = TokenStringUtils.formDataTokenToJson(bodyStr);
            }

            logger.error(json.toString());

            //得到TOKEN的值
            String jsonToken = json.get(TokenConstant.TOKEN).toString();

            if (jsonToken == null)
                return erorr(exchange, -1, "非法请求");

            System.err.println("url：" + request.getURI().getRawPath() + "  --TOKEN::" + jsonToken);

            //删除TOEKN 值
            json.remove(TokenConstant.TOKEN);
            StringBuffer sb = new StringBuffer();
            //application/x-www-form-urlencoded和application/json才添加参数
            //其他上传文件之类的，不做参数处理，因为文件流添加参数，文件原格式就会出问题了
            if (MediaType.APPLICATION_FORM_URLENCODED_VALUE.equalsIgnoreCase(contentType)
                    || MediaType.APPLICATION_JSON_VALUE.equalsIgnoreCase(contentType)) {
                userModel = new UserModel(json.get("name").getAsString(), json.get("age").getAsInt(), json.get("sex").getAsInt());
                json.remove("name");
                json.remove("age");
                json.remove("sex");
                userModel.setName("123");
                userModel.setAge(25);

                JsonObject userModelJson = new Gson().fromJson(userModel.toString(), JsonObject.class);

                bodyStr = TokenStringUtils.JsonToParameter(userModelJson);
                sb.append(bodyStr);
            }
            bodyStr = TokenStringUtils.JsonToParameter(json);
            if (sb.length() > 0)
                sb.append("&");
            sb.append(bodyStr);
            bodyStr = sb.toString();

            Mono<?> modifiedBody = Mono.just(bodyStr);

            BodyInserter bodyInserter = BodyInserters.fromPublisher(modifiedBody, inClass);
            HttpHeaders headers = new HttpHeaders();
            headers.putAll(exchange.getRequest().getHeaders());
            headers.remove(HttpHeaders.CONTENT_LENGTH);
            headers.set(HttpHeaders.CONTENT_TYPE, contentType);

            DataBuffer bodyDataBuffer = stringBuffer(bodyStr);
            Flux<DataBuffer> bodyFlux = Flux.just(bodyDataBuffer);

            // 由于修改了传递参数，需要重新设置CONTENT_LENGTH，长度是字节长度，不是字符串长度
            int length = bodyStr.getBytes().length;
            headers.remove(HttpHeaders.CONTENT_LENGTH);
            headers.setContentLength(length);

            CachedBodyOutputMessage outputMessage = new CachedBodyOutputMessage(exchange, headers);
            return bodyInserter.insert(outputMessage, new BodyInserterContext())
                    .then(Mono.defer(() -> {
                        ServerHttpRequestDecorator decorator = new ServerHttpRequestDecorator(exchange.getRequest()) {
                            @Override
                            public HttpHeaders getHeaders() {
                                long contentLength = headers.getContentLength();
                                HttpHeaders httpHeaders = new HttpHeaders();
                                httpHeaders.putAll(super.getHeaders());
                                if (contentLength > 0) {
                                    httpHeaders.setContentLength(contentLength);
                                } else {
                                    // TODO: this causes a 'HTTP/1.1 411 Length Required' on httpbin.org
                                    httpHeaders.set(HttpHeaders.TRANSFER_ENCODING, "chunked");
                                }
                                return httpHeaders;
                            }

                            @Override
                            public Flux<DataBuffer> getBody() {
                                return bodyFlux;
                            }
                        };
                        return chain.filter(exchange.mutate().request(decorator).build());
                    }));
        }
        return ok(exchange);
    }


    /**
     * 错误请求回退
     *
     * @param exchange
     * @param code         回退CODE
     * @param errorMessage 错误信息
     * @return 回退
     */
    private Mono<Void> erorr(ServerWebExchange exchange, int code, String errorMessage) {
        ServerHttpResponse response = exchange.getResponse();
        JsonObject gson = new JsonObject();
        gson.addProperty("Code", code);
        gson.addProperty("Message", errorMessage);
        byte[] bits = gson.toString().getBytes(StandardCharsets.UTF_8);
        DataBuffer buffer = response.bufferFactory().wrap(bits);
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        //指定编码，否则在浏览器中会中文乱码
        response.getHeaders().add("Content-Type", "text/plain;charset=UTF-8");
        return response.writeWith(Mono.just(buffer));
    }

    /**
     * 响应无结果的正确连接(Not P O S T)
     *
     * @param exchange
     * @return
     */
    private Mono<Void> ok(ServerWebExchange exchange) {
        ServerHttpResponse response = exchange.getResponse();
        JsonObject gson = new JsonObject();
        byte[] bits = gson.toString().getBytes(StandardCharsets.UTF_8);
        DataBuffer buffer = response.bufferFactory().wrap(bits);
        response.setStatusCode(HttpStatus.OK);
        //指定编码，否则在浏览器中会中文乱码
        response.getHeaders().add("Content-Type", "text/plain;charset=UTF-8");
        return response.writeWith(Mono.just(buffer));
    }

    /**
     * 从Flux<DataBuffer>中获取字符串的方法
     *
     * @return 请求体
     */
    private String resolveBodyFromRequest(Flux<DataBuffer> body) {
        //获取请求体
        StringBuilder sb = new StringBuilder();

        body.subscribe(buffer -> {
            byte[] bytes = new byte[buffer.readableByteCount()];
            buffer.read(bytes);
            DataBufferUtils.release(buffer);
            try {
                String bodyString = new String(bytes, "utf-8");
                sb.append(bodyString);
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
        });
        //获取request body
        return sb.toString();
    }

    /**
     * 字符串转DataBuffer
     *
     * @param value
     * @return
     */
    private DataBuffer stringBuffer(String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        NettyDataBufferFactory nettyDataBufferFactory = new NettyDataBufferFactory(ByteBufAllocator.DEFAULT);
        DataBuffer buffer = nettyDataBufferFactory.allocateBuffer(bytes.length);
        buffer.write(bytes);
        return buffer;
    }

    public static class Config {
    }
}
