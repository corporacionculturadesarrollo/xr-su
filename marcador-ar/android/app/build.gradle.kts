plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Tablero: copia `preview/marcador.html` + `fonts/*.ttf` a los assets en CADA build.
// Así la fuente de verdad sigue siendo `preview/marcador.html` (nunca se duplica a mano)
// y el relativo `../fonts/...` del HTML se resuelve porque `preview/` y `fonts/` son hermanos.
val dirTablero = layout.buildDirectory.dir("tablero-assets")

val sincronizarTablero = tasks.register<Sync>("sincronizarTablero") {
    into(dirTablero)
    from(file("../../preview")) {
        include("marcador.html")
        into("preview")
    }
    from(file("../../fonts")) {
        include("*.ttf")
        into("fonts")
    }
}

android {
    namespace = "com.marcadorar.app"
    compileSdk = 36

    // API key de la Geospatial API de ARCore: NUNCA en el repo. Viene de la
    // variable de entorno AR_GEOSPATIAL_KEY (o de local.properties, que está en
    // .gitignore). Sin ella, el modo geospatial da ERROR_NOT_AUTHORIZED y la app
    // sigue funcionando en modo temporal (sin fijado geográfico).
    val geoKey: String = (System.getenv("AR_GEOSPATIAL_KEY")
        ?: project.findProperty("AR_GEOSPATIAL_KEY") as String?
        ?: "")

    sourceSets {
        getByName("main") {
            assets.srcDir(dirTablero)
        }
    }

    buildFeatures {
        compose = true
        // Expone AR_GEOSPATIAL_KEY a BuildConfig para que VistaAr sepa si debe
        // activar el modo geospatial (sin key, ENABLED haría fallar la sesión).
        buildConfig = true
    }

    defaultConfig {
        applicationId = "com.marcadorar.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1"
        manifestPlaceholders["arGeospatialKey"] = geoKey
        buildConfigField("String", "AR_GEOSPATIAL_KEY", "\"$geoKey\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

dependencies {
    // El núcleo del protocolo es Kotlin puro: solo necesita JUnit para sus pruebas.
    testImplementation("junit:junit:4.13.2")
    // Transporte WebSocket para la app Android (la capa `core` sigue sin depender de esto).
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    // Servidor WebSocket embebido (el admin aloja la instancia). Corre en JVM, testeable de verdad.
    implementation("org.java-websocket:Java-WebSocket:1.6.0")

    // --- Vista AR del espectador (SceneView 4.x, Compose) ---
    // BOM de Compose: fija la versión de ui/foundation/material3.
    implementation(platform("androidx.compose:compose-bom:2026.06.01"))
    implementation("androidx.core:core-ktx:1.18.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    // SceneView: ARSceneView + ViewNode (renderiza una View de Android como textura 3D en AR).
    implementation("io.github.sceneview:arsceneview:4.25.0")
    // La Geospatial API de ARCore requiere la librería de ubicación de Play
    // Services; sin ella, habilitar el modo geospatial revienta la sesión
    // (AR_ERROR_GOOGLE_PLAY_SERVICES_LOCATION_LIBRARY_NOT_LINKED).
    implementation("com.google.android.gms:play-services-location:21.3.0")
}

// Los tasks de merge de assets deben copiar primero el tablero a `dirTablero`.
tasks.configureEach {
    if (name.startsWith("merge") && name.endsWith("Assets")) {
        dependsOn(sincronizarTablero)
    }
}

// Dev: arranca el servidor embebido de Kotlin para probar el tablero HTML
// contra él en un navegador real. `PUERTO=8897 ./gradlew servidorKotlin`.
tasks.register<JavaExec>("servidorKotlin") {
    group = "verification"
    description = "Arranca el servidor WebSocket de Kotlin (prueba HTML <-> servidor Kotlin)"
    mainClass.set("com.marcadorar.tools.LanzadorServidorKt")
    classpath = configurations["debugUnitTestRuntimeClasspath"] +
        files(layout.buildDirectory.dir("tmp/kotlin-classes/debugUnitTest"))
    dependsOn("compileDebugUnitTestKotlin")
}
