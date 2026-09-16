plugins {
	id("net.neoforged.moddev") version "+"
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
	runtimeOnly("maven.modrinth:jade:${p("jadeVersion")}+${p("loader")}")
	add("additionalRuntimeClasspath", mixinAgentNotation)
}
fun p(key: String) = property(key).toString()
println("Java: ${System.getProperty("java.version")}, JVM: ${System.getProperty("java.vm.version")} (${System.getProperty("java.vendor")}), Arch: ${System.getProperty("os.arch")}")
