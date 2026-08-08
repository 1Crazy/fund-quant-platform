package org.dromara.fund.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.exception.ServiceException;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

/** Java/Python 共享的配置 JSON 规范化和 SHA-256 计算规则。 */
@Component
@RequiredArgsConstructor
public class QuantConfigJsonSupport {

    private final ObjectMapper objectMapper;

    public JsonNode readObject(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            if (root == null || !root.isObject()) {
                throw new ServiceException("量化配置必须是 JSON 对象");
            }
            return root;
        } catch (JsonProcessingException e) {
            throw new ServiceException("量化配置 JSON 格式无效");
        }
    }

    public String canonicalize(JsonNode root) {
        try {
            return objectMapper.writeValueAsString(sort(root));
        } catch (JsonProcessingException e) {
            throw new ServiceException("量化配置规范化失败");
        }
    }

    public String checksum(String canonicalJson) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(canonicalJson.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JDK 缺少 SHA-256 算法", e);
        }
    }

    private JsonNode sort(JsonNode node) {
        if (node.isObject()) {
            ObjectNode target = objectMapper.createObjectNode();
            List<String> names = new ArrayList<>();
            node.fieldNames().forEachRemaining(names::add);
            names.sort(Comparator.naturalOrder());
            for (String name : names) {
                target.set(name, sort(node.get(name)));
            }
            return target;
        }
        if (node.isArray()) {
            ArrayNode target = objectMapper.createArrayNode();
            for (JsonNode child : node) {
                target.add(sort(child));
            }
            return target;
        }
        return node;
    }
}
