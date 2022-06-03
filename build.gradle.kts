import myaa.subkt.ass.*
import myaa.subkt.tasks.*
import myaa.subkt.tasks.Mux.*
import myaa.subkt.tasks.Anidex.*
import myaa.subkt.tasks.Nyaa.*
import java.awt.Color
import java.time.*

plugins {
    id("myaa.subkt")
}

fun String.isKaraTemplate(): Boolean {
    return this.startsWith("code") || this.startsWith("template") || this.startsWith("mixin")
}

fun EventLine.isKaraTemplate(): Boolean {
    return this.comment && this.effect.isKaraTemplate()
}

subs {
    readProperties("sub.properties", "../sekrit.properties")
    episodes(getList("episodes"))

    val op_ktemplate by task<Automation> {
        from(get("OP"))

        video(get("premux"))
        script("0x.KaraTemplater.moon")
        macro("0x539's Templater")
        loglevel(Automation.LogLevel.WARNING)
    }

    // val ed_ktemplate by task<Automation> {
    //     from(get("ED"))

    //     video(get("premux"))
    //     script("0x.KaraTemplater.moon")
    //     macro("0x539's Templater")
    //     loglevel(Automation.LogLevel.WARNING)
    // }

    merge {
        from(get("dialogue"))

		fromIfPresent(get("extra"), ignoreMissingFiles = true)

        if (propertyExists("OP")) {
            from(op_ktemplate.item()) {
                syncSourceLine("sync")
                syncTargetLine("opsync")
            }
        }

        if (propertyExists("ED")) {
            // from(ed_ktemplate.item()) {
            from(get("ED")) {
                syncSourceLine("sync")
                syncTargetLine("edsync")
            }
        }

        fromIfPresent(get("INS"), ignoreMissingFiles = true)
        fromIfPresent(getList("TS"), ignoreMissingFiles = true)

        includeExtraData(false)
        includeProjectGarbage(false)

        scriptInfo {
            title = get("group").get()
            scaledBorderAndShadow = true
        }
    }

    val cleanmerge by task<ASS> {
        from(merge.item())
        ass {
            events.lines.removeIf { it.isKaraTemplate() }
        }
    }

    chapters {
        from(cleanmerge.item())
        chapterMarker("chapter")
    }

    swap { from(cleanmerge.item()) }

    mux {
        title(get("title"))

        skipUnusedFonts(true)

        from(get("premux")) {
            video {
                lang("jpn")
                default(true)
            }
            audio(0) {
                lang("jpn")
                name("AAC 2.0")
                default(true)
                forced(false)
            }
            audio(1) {
                lang("jpn")
                name("AAC 5.1")
                default(false)
                forced(false)
            }
            includeChapters(false)
            attachments { include(false) }
        }

        from(cleanmerge.item()) {
            subtitles {
                lang("eng")
                name(get("group_reg"))
                default(true)
                forced(false)
                compression(CompressionType.ZLIB)
            }
        }

        from(swap.item()) {
            subtitles {
                lang("enm")
                name(get("group_hono"))
                default(false)
                forced(false)
                compression(CompressionType.ZLIB)
            }
        }

        chapters(chapters.item()) { lang("eng") }

        attach(get("common_fonts")) {
            includeExtensions("ttf", "otf", "ttc")
        }

        attach(get("fonts")) {
            includeExtensions("ttf", "otf", "ttc")
        }

        if (propertyExists("OP")) {
            attach(get("opfonts")) {
                includeExtensions("ttf", "otf", "ttc")
            }
        }

        if (propertyExists("ED")) {
            attach(get("edfonts")) {
                includeExtensions("ttf", "otf", "ttc")
            }
        }

        out(get("muxout"))
    }

    alltasks {
        torrent {
            trackers(getList("trackers"))
            from(mux.batchItems())
            comment("GJM")
            out(get("title_torrent2"))
        }

        nyaa {
            from(torrent.item())
            username(get("torrentuser"))
            password(get("torrentpass"))
            category(NyaaCategories.ANIME_ENGLISH)
            hidden(false)
            information(get("torrentinfo"))
            torrentDescription(getFile("torrent_desc_nyaa.txt"))
        }

        anidex {
            from(torrent.item())
            apiKey(get("anidexapikey"))
            category(AnidexCategories.ANIME_SUB)
            lang(AnidexLanguage.ENGLISH)
            torrentName(get("title_torrent"))
            torrentDescription(getFile("torrent_desc_anidex.txt"))
            group(112)
            hidden(false)
        }

        fun SFTP.configure() {
            host(get("ftphost"))
            username(get("ftpuser"))
            password(get("ftppass"))
            port(getAs<Int>("ftpport"))
            knownHosts("../known_hosts")
            identity("../id_rsa")
        }

        val uploadFiles by task<SFTP> {
            from(mux.item())
            configure()
            into(get("ftpfiledir"))
        }

        val checkFiles by task<SSHExec> {
            dependsOn(uploadFiles.item())
            host(get("sshhost"))
            username(get("sshusername"))
            port(getAs<Int>("sshport"))
            identity("../id_rsa")
            knownHosts("../known_hosts")
            command(get("crcCheck"))

            // TODO: Do ErrorMode.FAIL on CRC32 mismatch.
        }

        val startSeeding by task<SFTP> {
            // upload files to seedbox and publish to nyaa before initiating seeding
            dependsOn(uploadFiles.item(), nyaa.item(), anidex.item())
            from(torrent.item())
            configure()
            into(get("ftptorrentdir"))
        }
    }
}
