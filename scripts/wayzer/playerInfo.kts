package wayzer

import arc.util.Strings
import cf.wayzer.placehold.DynamicVar
import mindustry.gen.Groups
import mindustry.net.Administration
import mindustry.net.Packets
import org.jetbrains.exposed.sql.transactions.transaction
import wayzer.lib.event.PlayerJoin
import java.time.Duration
import java.util.*

name = "基础: 玩家数据"


registerVarForType<Player>().apply {
    registerChild("ext", "模块扩展数据", DynamicVar.obj { PlayerData[it.uuid()] })
    registerChild("profile", "统一账号信息(可能不存在)", DynamicVar.obj { PlayerData[it.uuid()].profile })
}

registerVarForType<Administration.PlayerInfo>().apply {
    registerChild("ext", "模块扩展数据", DynamicVar.obj { PlayerData[it.id] })
    registerChild("profile", "统一账号信息(可能不存在)", DynamicVar.obj { PlayerData[it.id].profile })
}

registerVarForType<PlayerData>().apply {
    registerChild("name", "名字", DynamicVar.obj { it.lastName })
    registerChild("uuid", "uuid", DynamicVar.obj { it.id.value })
    registerChild("firstJoin", "第一次进服", DynamicVar.obj { Date.from(it.firstTime) })
    registerChild("lastJoin", "最后在线", DynamicVar.obj { Date.from(it.lastTime) })
    registerChild("profile", "统一账号信息(可能不存在)", DynamicVar.obj { it.profile })
}

registerVarForType<PlayerProfile>().apply {
    registerChild("id", "绑定的账号ID(qq)", DynamicVar.obj { it.qq })
    registerChild("totalExp", "总经验", DynamicVar.obj { it.totalExp })
    registerChild("onlineTime", "总在线时间", DynamicVar.obj { Duration.ofSeconds(it.totalTime.toLong()) })
    registerChild("registerTime", "注册时间", DynamicVar.obj { Date.from(it.registerTime) })
    registerChild("lastTime", "账号最后登录时间", DynamicVar.obj { Date.from(it.lastTime) })
}

listen<EventType.PlayerConnect> {
    val p = it.player
    if (Groups.player.any { pp -> pp.uuid() == p.uuid() }) return@listen p.con.kick(Packets.KickReason.idInUse)
    if (Strings.stripColors(it.player.name).length > 24) return@listen p.con.kick("Name is too long")
    val data = PlayerData.findById(p.uuid())
    val event = PlayerJoin(p, data).apply { emit() }
    if (event.cancelled)
        p.kick("[red]拒绝入服: ${event.reason}")
    else transaction { PlayerData.findOrCreate(p).onJoin(p) }
}

listen<EventType.PlayerLeave> { event ->
    val p = event.player
    transaction {
        PlayerData.findOrCreate(p).onQuit(p)
    }
}

onEnable {
    launch(Dispatchers.IO) {
        delay(5000)
        val online = Groups.player.mapNotNull { PlayerData[it.uuid()].secureProfile(it) }
        transaction {
            online.forEach(PlayerProfile::loopCheck)
        }
    }
}