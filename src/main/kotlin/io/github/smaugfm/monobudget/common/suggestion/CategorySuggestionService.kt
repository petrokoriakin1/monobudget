package io.github.smaugfm.monobudget.common.suggestion

import io.github.smaugfm.monobudget.common.misc.MCC
import io.github.smaugfm.monobudget.common.model.settings.MccOverrideSettings
import mu.KotlinLogging
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

private val log = KotlinLogging.logger { }

abstract class CategorySuggestionService : KoinComponent {
    private val mccOverride: MccOverrideSettings by inject()

    abstract suspend fun categoryIdByName(categoryName: String): String?
    abstract suspend fun categoryNameById(categoryId: String?): String?
    abstract suspend fun categoryIdToNameList(): List<Pair<String, String>>

    fun categoryNameByMcc(mcc: Int): String? = mccOverride.mccToCategoryName[mcc] ?: categoryNameByMccGroup(mcc)

    suspend fun categoryIdByMcc(mcc: Int): String? = categoryNameByMcc(mcc)?.let { categoryIdByName(it) }

    private fun categoryNameByMccGroup(mcc: Int): String? {
        val mccObj = MCC.map[mcc]
        if (mccObj == null) {
            log.warn { "Unknown MCC code $mcc" }
        } else {
            val categoryName = mccOverride.mccGroupToCategoryName[mccObj.group.type]
            if (categoryName != null) {
                return categoryName
            }
        }
        return null
    }
}
