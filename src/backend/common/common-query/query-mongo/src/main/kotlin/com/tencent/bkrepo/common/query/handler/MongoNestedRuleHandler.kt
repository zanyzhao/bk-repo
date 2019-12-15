package com.tencent.bkrepo.common.query.handler

import com.tencent.bkrepo.common.query.builder.MongoQueryBuilder
import com.tencent.bkrepo.common.query.model.Rule
import org.springframework.data.mongodb.core.query.Criteria

/**
 *
 * @author: carrypan
 * @date: 2019/11/15
 */
interface MongoNestedRuleHandler {

    fun handle(rule: Rule.NestedRule, context: MongoQueryBuilder): Criteria
}