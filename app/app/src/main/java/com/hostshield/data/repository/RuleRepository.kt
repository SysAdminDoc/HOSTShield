package com.hostshield.data.repository

import com.hostshield.data.database.UserRuleDao
import com.hostshield.data.model.RuleType
import com.hostshield.data.model.UserRule
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RuleRepository @Inject constructor(
    private val ruleDao: UserRuleDao
) {
    fun getAllRules(): Flow<List<UserRule>> = ruleDao.getAllRules()
    fun getRulesByType(type: RuleType): Flow<List<UserRule>> = ruleDao.getByType(type)
    fun searchRules(query: String): Flow<List<UserRule>> = ruleDao.search(query)
    fun getRuleCount(type: RuleType): Flow<Int> = ruleDao.countByType(type)
    suspend fun addRule(rule: UserRule): Long = ruleDao.insert(rule)
    suspend fun updateRule(rule: UserRule) = ruleDao.update(rule)
    suspend fun deleteRule(rule: UserRule) = ruleDao.delete(rule)
    suspend fun toggleRule(id: Long, enabled: Boolean) = ruleDao.setEnabled(id, enabled)
    suspend fun ruleExists(hostname: String): Boolean = ruleDao.exists(hostname)
    suspend fun getEnabledWildcards(): List<UserRule> = ruleDao.getEnabledWildcards()
    suspend fun getEnabledRegexRules(): List<UserRule> = ruleDao.getEnabledRegexRules()
    suspend fun getEnabledRulesByType(type: RuleType): List<UserRule> = ruleDao.getEnabledByType(type)
}
