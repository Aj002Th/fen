package app.fen.mcp.tools

import app.fen.mcp.write.WriteService
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class WriteTools(private val service: WriteService) {

    fun descriptors(): List<ToolDescriptor> = listOf(
        ToolDescriptor(
            name = "add_transactions",
            description = "批量新增流水（1–20 条）。amount 用元字符串；category_id 来自 list_categories；client_request_id 用于幂等重试。",
            inputSchema = addSchema(),
            kind = ToolKind.WRITE,
            handler = { args -> service.add(args) },
        ),
        ToolDescriptor(
            name = "update_transaction",
            description = "修改一条流水。patch 至少一个字段；改 type 时必须同时给新类型的 category_id 或 null。",
            inputSchema = updateSchema(),
            kind = ToolKind.WRITE,
            handler = { args -> service.update(args) },
        ),
        ToolDescriptor(
            name = "delete_transactions",
            description = "删除 1–50 条流水。会记录批次，可在手机上整批撤销。",
            inputSchema = deleteSchema(),
            kind = ToolKind.WRITE,
            handler = { args -> service.delete(args) },
        ),
    )

    private fun addSchema(): JsonObject = buildJsonObject {
        put("type", "object")
        put("additionalProperties", false)
        put("required", JsonArray(listOf(JsonPrimitive("client_request_id"), JsonPrimitive("transactions"))))
        put(
            "properties",
            buildJsonObject {
                put("client_request_id", idProperty())
                put(
                    "transactions",
                    buildJsonObject {
                        put("type", "array")
                        put("minItems", 1)
                        put("maxItems", 20)
                        put(
                            "items",
                            buildJsonObject {
                                put("type", "object")
                                put("additionalProperties", false)
                                put("required", JsonArray(listOf(JsonPrimitive("type"), JsonPrimitive("amount"))))
                                put(
                                    "properties",
                                    buildJsonObject {
                                        put("type", enumProperty(listOf("expense", "income")))
                                        put("amount", amountProperty())
                                        put("category_id", nullableStringProperty())
                                        put("note", stringProperty("备注，最多 200 字", maxLength = 200))
                                        put("occurred_at", stringProperty("ISO-8601 带时区；缺省为现在"))
                                    },
                                )
                            },
                        )
                    },
                )
            },
        )
    }

    private fun updateSchema(): JsonObject = buildJsonObject {
        put("type", "object")
        put("additionalProperties", false)
        put("required", JsonArray(listOf(JsonPrimitive("id"), JsonPrimitive("patch"), JsonPrimitive("client_request_id"))))
        put(
            "properties",
            buildJsonObject {
                put("id", stringProperty("流水 id"))
                put("client_request_id", idProperty())
                put(
                    "patch",
                    buildJsonObject {
                        put("type", "object")
                        put("additionalProperties", false)
                        put(
                            "properties",
                            buildJsonObject {
                                put("type", enumProperty(listOf("expense", "income")))
                                put("amount", amountProperty())
                                put("category_id", nullableStringProperty())
                                put("note", stringProperty("备注，最多 200 字", maxLength = 200))
                                put("occurred_at", stringProperty("ISO-8601 带时区"))
                            },
                        )
                    },
                )
            },
        )
    }

    private fun deleteSchema(): JsonObject = buildJsonObject {
        put("type", "object")
        put("additionalProperties", false)
        put("required", JsonArray(listOf(JsonPrimitive("ids"), JsonPrimitive("client_request_id"))))
        put(
            "properties",
            buildJsonObject {
                put(
                    "ids",
                    buildJsonObject {
                        put("type", "array")
                        put("minItems", 1)
                        put("maxItems", 50)
                        put("items", buildJsonObject { put("type", "string") })
                    },
                )
                put("client_request_id", idProperty())
            },
        )
    }

    private fun idProperty(): JsonObject = buildJsonObject {
        put("type", "string")
        put("minLength", 8)
        put("maxLength", 64)
        put("description", "幂等键；相同请求重试不会重复写入")
    }

    private fun amountProperty(): JsonObject = buildJsonObject {
        put("type", "string")
        put("pattern", "^\\d{1,9}(\\.\\d{1,2})?$")
        put("description", "金额（元），如 32.50")
    }

    private fun nullableStringProperty(): JsonObject = buildJsonObject {
        put("type", JsonArray(listOf(JsonPrimitive("string"), JsonPrimitive("null"))))
        put("description", "分类 id；可显式传 null")
    }

    private fun stringProperty(description: String, maxLength: Int? = null): JsonObject = buildJsonObject {
        put("type", "string")
        put("description", description)
        if (maxLength != null) put("maxLength", maxLength)
    }

    private fun enumProperty(values: List<String>): JsonObject = buildJsonObject {
        put("type", "string")
        put("enum", JsonArray(values.map { JsonPrimitive(it) }))
    }
}
