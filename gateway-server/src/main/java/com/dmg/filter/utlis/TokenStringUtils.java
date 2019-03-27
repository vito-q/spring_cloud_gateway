package com.dmg.filter.utlis;

import com.dmg.Constants.TokenConstant;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

public class TokenStringUtils {

    /**
     * form-data 方式提交数据转JSON
     *
     * @param bodyStr body数据
     * @return json对象
     */
    public static JsonObject formDataTokenToJson(String bodyStr) {
        JsonObject json = new JsonObject();
        String[] bodys = bodyStr.split(TokenConstant.TOKENSPLIT0);
        String split = TokenConstant.TOKENSPLIT1 + bodys[0];
        for (int i = 1; i < bodys.length; i++) {
            String[] strs = bodys[i].split(split);
            strs = strs[0].split(TokenConstant.TOKENSPLIT2);
            json.addProperty(strs[0], strs[1]);
        }
        return json;
    }

    public static JsonObject x_www_form_urlencodedToJson(String bodyStr) {
        JsonObject json = new JsonObject();
        bodyStr = "{" + bodyStr + "}";
        bodyStr = bodyStr.replaceAll(TokenConstant.TOKENSPLIT3, ",");
        bodyStr = bodyStr.replaceAll(TokenConstant.TOKENSPLIT4, ":");
        json = new Gson().fromJson(bodyStr, JsonObject.class);
        return json;
    }

    public static String JsonToParameter(JsonObject json) {
        if (json == null || json.size() < 1)
            return "";
        StringBuffer sb = new StringBuffer();
        json.entrySet().forEach(entrySet -> {
            String key = entrySet.getKey();
            String value = entrySet.getValue().getAsString();
            if (sb.length() < 1) {
                sb.append(key);
                sb.append("=");
                sb.append(value);
            } else {
                sb.append("&");
                sb.append(key);
                sb.append("=");
                sb.append(value);
            }
        });

        return sb.toString();
    }


}
