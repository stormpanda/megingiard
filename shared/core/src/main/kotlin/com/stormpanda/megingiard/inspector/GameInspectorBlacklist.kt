package com.stormpanda.megingiard.inspector

import com.stormpanda.megingiard.AppLog

private const val TAG = "GameInspectorBlacklist"

/**
 * Result of a deterministic anti-cheat blacklist check.
 */
data class BlacklistMatch(
    val packageName: String,
    val matchedRule: String,
)

/**
 * Deterministic registry of online games and publishers with kernel/user-space anti-cheat
 * systems that ban players for graphics layer interception or process attachment.
 *
 * Adheres strictly to AGENTS.md §7.5 (Zero-Heuristics): only exact package names or
 * explicit publisher namespace prefixes (`com.publisher.`) are matched.
 */
object GameInspectorBlacklist {
    val BLACKLISTED_PACKAGES: Set<String> =
        setOf(
            "com.miHoYo.Yuanshen",
            "com.miHoYo.Genshincn",
            "com.miHoYo.GenshinImpact",
            "com.miHoYo.hkrpg",
            "com.miHoYo.hkrpgoversea",
            "com.miHoYo.Nap",
            "com.miHoYo.bh3",
            "com.miHoYo.bh3global",
            "com.HoYoverse.hkrpgoversea",
            "com.HoYoverse.Nap",
            "com.kurogame.wutheringwaves.global",
            "com.kurogame.mingchao",
            "com.kurogame.haru.global",
            "com.tencent.ig",
            "com.tencent.tmgp.sgame",
            "com.tencent.tmgp.pubgmhd",
            "com.tencent.tmgp.cod",
            "com.tencent.lolm",
            "com.pubg.imobile",
            "com.pubg.newstate",
            "com.activision.callofduty.shooter",
            "com.activision.callofduty.warzone",
            "com.garena.game.codm",
            "com.garena.game.kgvn",
            "com.dts.freefireth",
            "com.dts.freefiremax",
            "com.riotgames.league.wildrift",
            "com.riotgames.league.teamfighttactics",
            "com.riotgames.valorant",
            "com.epicgames.fortnite",
            "com.roblox.client",
            "com.nianticlabs.pokemongo",
            "com.nianticlabs.monsterhunter",
            "com.supercell.brawlstars",
            "com.supercell.clashofclans",
            "com.supercell.clashroyale",
            "com.levelinfinite.sgameGlobal",
            "com.levelinfinite.hotta.gp",
            "com.proximabeta.nikke",
            "com.proximabeta.mf.uamo",
            "com.infoldgames.infinitynikkien",
            "com.gryphline.arknights.endfield",
            "com.hypergryph.arknights",
            "com.YoStarEN.Arknights",
            "com.YoStarEN.AzurLane",
            "com.nexon.bluearchive",
            "com.netmarble.sololv",
        )

    val BLACKLISTED_PREFIXES: List<String> =
        listOf(
            "com.miHoYo.",
            "com.HoYoverse.",
            "com.kurogame.",
            "com.tencent.",
            "com.proximabeta.",
            "com.levelinfinite.",
            "com.pubg.",
            "com.activision.",
            "com.riotgames.",
            "com.ea.gp.",
            "com.ea.game.",
            "com.epicgames.",
            "com.garena.",
            "com.netmarble.",
            "com.nexon.",
            "com.supercell.",
            "com.nianticlabs.",
            "com.infoldgames.",
            "com.gryphline.",
            "com.hypergryph.",
            "com.bilibili.",
        )

    fun isBlacklisted(packageName: String?): Boolean = getBlockReason(packageName) != null

    fun getBlockReason(packageName: String?): BlacklistMatch? {
        val pkg = packageName?.trim().orEmpty()
        if (pkg.isEmpty()) return null

        if (pkg in BLACKLISTED_PACKAGES) {
            AppLog.d(TAG, "getBlockReason: exact match for $pkg")
            return BlacklistMatch(packageName = pkg, matchedRule = pkg)
        }

        val matchedPrefix = BLACKLISTED_PREFIXES.firstOrNull { prefix -> pkg.startsWith(prefix) }
        if (matchedPrefix != null) {
            AppLog.d(TAG, "getBlockReason: prefix match ($matchedPrefix*) for $pkg")
            return BlacklistMatch(packageName = pkg, matchedRule = "$matchedPrefix*")
        }

        return null
    }
}
