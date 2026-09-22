plugins {
	id("net.neoforged.moddev") version "+"
	id("me.modmuss50.mod-publish-plugin") version "+"
}
base.archivesName.set(p("modName"))
group = p("modGroupId")
version = "${p("mcVersion")}-${p("modVersion")}-${p("loaderCap")}"
java.toolchain.languageVersion.set(JavaLanguageVersion.of(p("javaVersion")))
java.withSourcesJar()
tasks.jar { from("LICENSE") }
val generateMetadata = tasks.register<ProcessResources>("generateMetadata") {
	description = "Generate this project metadata from templates."
	val values = project.extra.properties.mapValues { it.value.toString() }
	inputs.properties(values)
	expand(values)
	from("src/main/templates")
	into("build/generated/sources/modMetadata")
}
sourceSets.main.get().resources.srcDir(generateMetadata)
val mixinAgentNotation = "dev.vfyjxf:mixin-hotswap-agent:${p("mixinAgentVersion")}"
val mixinAgent = configurations.create("mixinAgent").defaultDependencies { add(dependencyFactory.create(mixinAgentNotation)) }
neoForge {
	version = p("loaderVersion")
	parchment {
		mappingsVersion.set(p("parchmentVersion"))
		minecraftVersion.set(p("mcVersion"))
	}
	runs {
		create("client").client()
		create("server") {
			server()
			gameDirectory.set(file("run/server"))
			programArgument("--nogui")
		}
		configureEach {
			logLevel = org.slf4j.event.Level.INFO
			systemProperty("terminal.jline", "true")
			jvmArgument("-XX:+IgnoreUnrecognizedVMOptions")
			jvmArgument("-XX:+AllowEnhancedClassRedefinition")
			jvmArgument("-javaagent:${mixinAgent.files.first().toPath()}")
			// jvmArgument("-XX:-OmitStackTraceInFastThrow") // uncomment when you get exceptions with null messages etc
			// jvmArgument("-XX:+UnlockCommercialFeatures") // uncomment for profiling
		}
	}
	accessTransformers.publish(file("src/main/resources/META-INF/accesstransformer.cfg"))
	mods.create(p("modId")).sourceSet(sourceSets.main.get())
}
repositories {
	mavenLocal()
	mavenCentral()
	maven("https://maven.createmod.net") // Create, Ponder, Flywheel
	maven("https://mvn.devos.one/snapshots") // Registrate
	maven("https://maven.ryanhcode.dev/releases") // Aeronautics
	maven("https://maven.blamejared.com") // JEI, Veil, Ars Nouveau
	maven("https://maven.terraformersmc.com") // EMI
	maven("https://api.modrinth.com/maven") { content { includeGroup("maven.modrinth") } } // Modrinth
	maven("https://dl.zznty.ru/maven") // Create Factory Abstractions
}
dependencies {
	//region Create
	implementation("com.simibubi.create:create-${p("mcVersion")}:${p("createVersion")}") { isTransitive = false }
	implementation("dev.engine-room.flywheel:flywheel-${p("loader")}-${p("mcVersion")}:${p("flywheelVersion")}")
	implementation("net.createmod.ponder:ponder-${p("loader")}:${p("ponderVersion")}+mc${p("mcVersion")}") { isTransitive = false }
	implementation("com.tterrag.registrate:Registrate:${p("registrateVersion")}")
	//endregion
	//region Create Aeronautics
	implementation("dev.simulated_team.simulated:simulated-${p("loader")}-${p("mcVersion")}:${p("areoVersion")}") { isTransitive = false }
	implementation("dev.ryanhcode.offroad:offroad-${p("loader")}-${p("mcVersion")}:${p("areoVersion")}") { isTransitive = false }
	implementation("dev.eriksonn.aeronautics:aeronautics-${p("loader")}-${p("mcVersion")}:${p("areoVersion")}") { isTransitive = false }
	implementation("dev.ryanhcode.sable:sable-${p("loader")}-${p("mcVersion")}:${p("sableVersion")}") { isTransitive = false }
	implementation("dev.ryanhcode.sable-companion:sable-companion-common-${p("mcVersion")}:${p("sableCompanionVersion")}") { isTransitive = false }
	implementation("foundry.veil:veil-${p("loader")}-${p("mcVersion")}:${p("veilVersion")}")
	//endregion
	runtimeOnly("maven.modrinth:jade:${p("jadeVersion")}+${p("loader")}")
	add("additionalRuntimeClasspath", mixinAgentNotation)
}
publishMods {
	file.set(tasks.jar.get().archiveFile)
	changelog.set(file("CHANGELOG.md").readText())
	type.set(BETA)
	version.set(project.version.toString())
	displayName.set("[${p("loaderCap")}] ${p("modVersion")}")
	modLoaders.addAll(p("loaderCap"))
	modrinth {
		additionalFile(tasks.named<Jar>("sourcesJar")) { type.set(SOURCES_JAR) }
		accessToken.set(providers.environmentVariable("MODRINTH_TOKEN"))
		projectId.set("6WBXLjMf")
		minecraftVersions.add(p("mcVersion"))
		environment.set(CLIENT_AND_SERVER)
		optional("create")
	}
	curseforge {
		additionalFiles.from(tasks.named<Jar>("sourcesJar"))
		accessToken.set(providers.environmentVariable("CURSEFORGE_TOKEN"))
		projectId.set("1702964")
		minecraftVersions.add(p("mcVersion"))
		client.set(true)
		server.set(true)
		optional("create")
	}
}
fun p(key: String) = property(key).toString()
println("Java: ${System.getProperty("java.version")}, JVM: ${System.getProperty("java.vm.version")} (${System.getProperty("java.vendor")}), Arch: ${System.getProperty("os.arch")}")
