# Pausa — MVP del hackathon antiphishing

> **Este README reemplaza la reunión de kickoff.** Léelo completo (unos 15 minutos) antes de escribir código. Si no estás de acuerdo con algo de la sección [Decisiones](#4-decisiones-tomadas), dilo en el grupo **antes del domingo 25 de octubre a las 12:00 m.** Pasada esa hora, las damos por aceptadas.

---

## 1. Qué vamos a construir

**Pausa** es una app Android que, justo después de una llamada de un número desconocido, aparece encima de la app bancaria que la persona abre, le hace 3 preguntas y la lleva a verificar antes de mover dinero.

| | |
| --- | --- |
| **Problema** | Después de una llamada de suplantación ("tu hijo tuvo un accidente", "habla el banco"), la persona abre el banco y transfiere bajo presión antes de verificar. Con Bre-B el dinero sale en segundos. |
| **Usuario** | Población general en Colombia con Android. Usuario extremo: adultos mayores. |
| **Idea clave** *(hipótesis)* | En el pánico nadie recuerda verificar, pero todos tienen que abrir el banco para pagar: es el único paso que el estafador no puede saltarse. |
| **Propuesta de valor** | Justo cuando vas a mover dinero después de una llamada sospechosa, Pausa te detiene, te dice qué revisar y te deja verificar con un toque. |

### Cómo funciona

1. Termina una llamada de un número que **no está en los contactos** → se abre una **ventana de 15 minutos**.
2. Dentro de esa ventana, la persona abre una **app marcada como sensible** (banco, billetera) → **Pausa aparece encima**, a pantalla completa.
3. Tres preguntas de Sí o No:
   - ¿Te pidieron transferir, pagar o dar un código?
   - ¿Te presionaron con urgencia o miedo?
   - ¿Dijeron ser de un banco, una entidad o un familiar? → si es Sí: ¿quién? (familiar / entidad)
4. Todo No → "Todo parece normal" y continúa.
5. Algún Sí → consejo según el caso y tres botones:
   - **Verificar ahora:** abre el marcador con el número del familiar o el número oficial de la entidad.
   - **Es una estafa:** muestra qué hacer.
   - **Continuar de todos modos:** se habilita después de 10 segundos.
6. Cada evento queda registrado en el celular para mostrar métricas en la demo.

### Alcance

**Dentro del MVP:** detección de llamada y número desconocido · detección de app sensible · pop-up con preguntas, consejo y botones · lista curada de números oficiales · bienvenida que guía los permisos · configuración (apps sensibles, ventana, modo demo) · registro e historial · prueba con 3 a 5 usuarios.

**Fuera del MVP** (Camilo los muestra como hoja de ruta en Figma): simulacros, bloqueo en frío, botón de pánico, análisis del contenido de la llamada, iPhone, integración con bancos, servidor propio, Play Store.

Ningún dato sale del celular: no hay servidor. Es un punto a favor frente a la Ley 1581 y lo vamos a defender en la presentación.

---

## 2. Qué hacer hoy (sábado 24)

1. Leer este README completo.
2. Enviar tu usuario de GitHub a Brayan para que te dé acceso.
3. Clonar el repositorio y correr la app en tu celular ([sección 5](#5-cómo-correr-el-proyecto)).
4. Escribir en el grupo la **marca, el modelo y la versión de Android** de tu celular (Ajustes → Acerca del teléfono).
5. Empezar tu tarea del día según el [cronograma](#7-roles-y-cronograma). Si hoy no alcanzas, no pasa nada: lo importante es que mañana todos tengan el proyecto corriendo.

---

## 3. Roles

| Integrante | Rol | Responde por | Paquetes |
| --- | --- | --- | --- |
| **Brayan** | Líder técnico e integración | Pop-up y su flujo, modo demo, integración, APK final, integración | `overlay/`, `content/ContentRepository.kt` |
| **Juan David** | Servicio en segundo plano | Detección de llamada, número desconocido y app sensible; pruebas en varias marcas | `service/`, `detection/` |
| **Julian** | App y calidad | Bienvenida y permisos, configuración, registro e historial, control de calidad | `ui/`, `settings/`, `log/` |
| **Camilo** | Contenido, diseño y usuarios | Preguntas y consejos, números oficiales, Figma (incluida la hoja de ruta), prueba con usuarios, video | `assets/content/`, Figma |

Los roles se pueden cambiar si alguien domina mejor otra parte: díganlo en el grupo antes del domingo a las 12:00 m.

---

## 4. Decisiones tomadas

| Decisión | Por qué |
| --- | --- |
| **Opción A:** solo el pop-up, bien hecho | Cabe en el tiempo (~82 de 88 horas) y deja espacio para probar con usuarios. Las otras ideas van como hoja de ruta. |
| **Android nativo en Kotlin** | Es lo que más maneja el equipo, e iPhone no permite detectar llamadas ni mostrarse encima de otras apps. |
| **minSdk 31 (Android 12+), targetSdk 35** | Con Android 12+ basta una sola API para detectar llamadas (`TelephonyCallback`). **Si tu celular tiene Android 10 u 11, avisa ya.** |
| **Vistas XML + ViewBinding** (no Compose) | Mostrar Compose encima de otras apps requiere configuración extra. Usamos lo mismo en toda la app. |
| **Sin servidor** | Privacidad y menos cosas que pueden fallar. |
| **Sin servicio de accesibilidad** | Es la puerta que más usa el malware; un jurado de ciberseguridad lo vería mal. Usamos UsageStats. |
| **Ventana de 15 minutos** después de la llamada | Configurable en la app. |
| **Modo demo y video de respaldo obligatorios** | No sabemos cómo será la demo; no podemos depender de una llamada real. |

### Pendiente de confirmar

- [x] **Fecha de la presentación:** 6 de noviembre (confirmada). El plan congela el código el 3.
- [ ] **Celular de la demo:** marca y versión de Android (cada uno reporta el suyo hoy).
- [ ] **Apps sensibles por defecto** (Julian) y **entidades de la lista de números oficiales** (Camilo): los bancos y billeteras que más usan ustedes y sus familias.
- [ ] **Nombre definitivo:** "Pausa" es provisional.

---

## 5. Cómo correr el proyecto

**Requisitos:** Android Studio Koala o más reciente (trae el JDK 17) y un celular Android 12+ con cable USB.

1. Clonar: `git clone <URL del repo>`, cambiar a la rama `develop` (`git checkout develop`) y abrir la carpeta en Android Studio (**File → Open**).
2. Esperar a que termine **Gradle Sync**. La primera vez descarga dependencias y puede tardar varios minutos.
   - Si Android Studio dice que la versión del plugin de Android no es compatible, acepta el asistente de actualización y **avisa en el grupo** antes de subir el cambio.
3. En el celular: activar **Opciones de desarrollador** (tocar 7 veces "Número de compilación") y **Depuración por USB**.
4. Conectar el celular y pulsar **Run ▶**.
5. En la app de desarrollo, conceder los permisos con los botones:
   - Llamadas, contactos y notificaciones (diálogo normal).
   - **Mostrar sobre otras apps** y **Acceso a uso** (te llevan a Ajustes).
   - Si Android no te deja activarlos (pasa en Android 15 con APK instalados desde un archivo): **Ajustes → Apps → Pausa → menú ⋮ → Permitir configuración restringida**, y vuelve a intentarlo.
6. Pulsa **Probar pop-up (modo demo)**: debe aparecer la tarjeta "Antes de mover dinero". **Cerrar** la quita.
7. Pulsa **Iniciar protección**: debe aparecer la notificación fija "Pausa está activa".

Si los pasos 6 y 7 funcionan, tu entorno está listo.

---

## 6. Estructura del proyecto

```
app/src/main/
├── AndroidManifest.xml            Permisos y servicio (cambios: avisar en el grupo)
├── assets/content/
│   ├── preguntas.json             Preguntas, consejos y pasos ante estafa ............ Camilo
│   └── numeros_oficiales.json     Números oficiales con su fuente .................... Camilo
├── java/co/edu/unisabana/pausa/
│   ├── PausaApp.kt                Arranque de la app y canal de notificaciones
│   ├── overlay/                   Pop-up encima de otras apps ........................ Brayan
│   │   ├── Trigger.kt             ┐
│   │   ├── Decision.kt            ├ CONTRATO DE INTEGRACIÓN (ver sección 8)
│   │   └── OverlayController.kt   ┘
│   ├── service/GuardService.kt    Servicio en primer plano ........................... Juan David
│   ├── detection/                 CallMonitor, UnknownNumberChecker,
│   │                              ForegroundAppWatcher, VentanaDeRiesgo ............... Juan David
│   ├── content/                   Lector de los JSON ................................. Brayan
│   ├── settings/                  Ajustes con DataStore .............................. Julian
│   ├── log/                       Registro de eventos con Room ....................... Julian
│   ├── ui/                        MainActivity (temporal), onboarding, ajustes,
│   │                              historial .......................................... Julian
│   └── util/Permisos.kt           Consulta de permisos e intents a Ajustes
└── res/layout/
    ├── activity_main.xml          Pantalla temporal de desarrollo
    └── overlay_pausa.xml          Vista base del pop-up .............................. Brayan
```

Cada archivo pendiente tiene un comentario `TODO(Nombre, fecha)` con pistas técnicas. Búscalos con **Edit → Find → Find in Files → "TODO("**.

---

## 7. Roles y cronograma

Cada uno dedica unas **2 horas diarias**, incluidos los fines de semana.

| Día | Brayan | Juan David | Julian | Camilo |
| --- | --- | --- | --- | --- |
| Sáb 24 oct | Proyecto base y repositorio | Detectar el fin de la llamada | Estructura de la configuración | Bocetos del pop-up en Figma |
| Dom 25 | Pop-up encima de otra app | Número desconocido | Bienvenida y permisos | Preguntas y consejos |
| Lun 26 | Pop-up con las 3 preguntas | App sensible + servicio | Lista de apps sensibles | Números oficiales; diseño final |
| **Mar 27** | **Punto de control 1** · integración | Integración con Brayan | Prueba de Android 15 en el celular de la demo | Guion de la prueba con usuarios |
| Mié 28 | Consejo por caso y botones | Ventana, reinicio y batería | Registro e historial | Textos y números en la app |
| Jue 29 | Modo demo y cuenta regresiva | Prueba en dos marcas | Pruebas y lista de errores | Hoja de ruta en Figma |
| **Vie 30** | **Punto de control 2** · APK v0.9 | Todos prueban | Todos prueban | Todos prueban |
| Sáb 31 | Corrige errores | Corrige errores | Observa la prueba con usuarios | Conduce la prueba con usuarios |
| Dom 1 nov | Pulido visual | Corrige errores | Consolida errores | Prueba y resumen de resultados |
| Lun 2 | Ajustes y APK firmado | README técnico y diagrama | Video de respaldo | Video y resultados para el pitch |
| **Mar 3** | **Código congelado (12:00 m.)** · ensayo de la demo | Ensayo | Ensayo | Ensayo |

### Puntos de control (videollamada corta, los cuatro)

- **Martes 27 de octubre:** con una llamada real de un número no guardado, el pop-up aparece al abrir una app marcada. Si no aparece, Brayan y Juan David dedican el 28 completo a eso y se aplaza el historial.
- **Viernes 30 de octubre:** todo lo de "Dentro del MVP" funciona (salvo la prueba con usuarios) en un APK instalado en al menos dos celulares de marcas distintas.
- **Martes 3 de noviembre:** código congelado al mediodía, etiqueta `v1.0`, APK firmado y video de respaldo listos.

---

## 8. Contrato de integración

Es lo que permite que Juan David y Brayan trabajen en paralelo y se unan el 27. **No se cambia sin avisar en el grupo.**

```kotlin
// Lo crea el servicio (Juan David) cuando se abre una app sensible dentro de la ventana
data class Trigger(
    val appPackage: String,     // app sensible que se abrió
    val callNumber: String?,    // número de la llamada (null si no se pudo leer o en demo)
    val callEndedAt: Long,      // cuándo terminó la llamada
    val isDemo: Boolean = false
)

enum class Decision { VERIFICAR, ESTAFA, CONTINUAR }

// Lo implementa Brayan
OverlayController.show(context, trigger) { decision ->
    // el servicio guarda el EventoPausa (Julian) con la decisión
}
```

Flujo esperado dentro de `GuardService`:

```
CallMonitor: terminó una llamada
  → UnknownNumberChecker: ¿número desconocido?
      → sí: VentanaDeRiesgo.abrir(numero, minutos)
ForegroundAppWatcher (solo con la ventana abierta): se abrió el paquete X
  → ¿X está en las apps sensibles?
      → sí: VentanaDeRiesgo.cerrar(); OverlayController.show(context, Trigger(...))
```

---

## 9. Forma de trabajo

### Cómo subimos cambios (rama `develop`, sin pull requests)

Hay dos ramas fijas y no usamos PR ni revisión formal:

- **`develop`:** donde trabajamos todos. Es la rama por defecto.
- **`main`:** solo versiones probadas. Solo Brayan fusiona `develop` en `main`, en los puntos de control (27 oct, 30 oct y 3 nov), después de compilar y probar en un celular real. El APK y la etiqueta `v1.0` salen de `main`.

Reglas para todos:

- **`develop` siempre debe compilar y correr.** Si alguien lo rompe, lo arregla ese mismo día.
- Cada uno toca **solo sus paquetes** (sección 3). Así casi nunca hay conflictos.
- Commits pequeños y en español: `Detecta el fin de la llamada con TelephonyCallback`.
- Antes de empezar a trabajar: `git checkout develop` y `git pull --rebase origin develop`.
- Antes de subir: `git pull --rebase origin develop`, compila, prueba en el celular y `git push origin develop`.
- **Nadie sube directo a `main`.**
- Si tu cambio toca algo que otro usa (contrato de integración, permisos, Manifest, textos, versiones en `libs.versions.toml`), **avisa en el grupo antes de subirlo**.
- Si el cambio es grande o riesgoso, puedes usar una rama `feat/<nombre>-<tema>` y fusionarla tú mismo en `develop` cuando funcione; no hace falta revisión.

### Definición de "terminado" para cualquier tarea

- [ ] Compila y corre en un celular real (no solo en el emulador).
- [ ] Está en `develop` y `develop` sigue compilando.
- [ ] No rompe el flujo principal: llamada (o modo demo) → app sensible → pop-up.
- [ ] Si cambia algo que otro usa (contrato, permisos, Manifest, textos), se avisó en el grupo.

### Errores

Repórtalos como **Issue** en GitHub con la plantilla "Error" e indica la severidad:

- **Bloqueante:** rompe el flujo principal o cierra la app → se corrige ese mismo día.
- **Importante:** funciona, pero mal → se corrige antes del 2 de noviembre.
- **Menor:** detalle visual o de texto → si da el tiempo.

### Comunicación

- **Bloqueos:** si llevas más de 30 minutos atascado, escribe en el grupo qué intentaste. Es mejor preguntar temprano.
- **Avance diario:** al terminar tu bloque del día, un mensaje corto en el grupo con lo que hiciste, lo que sigue y si algo te bloquea.

---

## 10. Permisos de Android y restricciones

| Necesidad | API | Permiso |
| --- | --- | --- |
| Saber que terminó una llamada | `TelephonyManager` + `TelephonyCallback.CallStateListener` | `READ_PHONE_STATE` |
| Saber si el número era desconocido | `CallLog.Calls` + `ContactsContract.PhoneLookup` | `READ_CALL_LOG`, `READ_CONTACTS` |
| Saber qué app se abre | `UsageStatsManager.queryEvents` cada ~1 s durante la ventana | Acceso a uso (en Ajustes) |
| Mostrar el pop-up encima | `WindowManager` + `TYPE_APPLICATION_OVERLAY` | Mostrar sobre otras apps (en Ajustes) |
| Seguir vivo en segundo plano | Servicio en primer plano tipo `specialUse` | `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`, `POST_NOTIFICATIONS` |

**Android 15:** si la app se instala desde un APK (no desde Play Store), "Mostrar sobre otras apps" y "Acceso a uso" quedan bloqueados hasta que el usuario entra a *Información de la app → menú ⋮ → Permitir configuración restringida*. La bienvenida debe guiar este paso ([fuente](https://androidauthority.com/android-15-restricted-settings-sideloading-3481098)).

**Fabricantes (Xiaomi, Samsung, Huawei…):** algunos cierran servicios en segundo plano. Hay que pedir al usuario que quite la optimización de batería para Pausa y probarlo en varias marcas (Juan David, 29 oct).

---

## 11. Problemas comunes

| Problema | Solución |
| --- | --- |
| Gradle Sync falla al descargar | Revisa la conexión y vuelve a intentar con **File → Sync Project with Gradle Files**. Si sigue, envía el error al grupo. |
| El pop-up no aparece | Revisa que "Mostrar sobre otras apps" esté activo (la pantalla de desarrollo lo indica). |
| No me deja activar "Acceso a uso" | Es la restricción de Android 15: ver sección 10. |
| La app se cierra al iniciar la protección | Copia el error de **Logcat** (filtra por `pausa`) y abre un Issue bloqueante. |
| El celular no aparece en Android Studio | Revisa la depuración USB, acepta el aviso de "Permitir depuración" en el celular y prueba otro cable. |

---

## 12. Documentos relacionados

- Definición de la solución y plan de trabajo (Opción A).
- Proceso de selección del concepto (tamizado y Matriz de Pugh).
- Diseño del pop-up y hoja de ruta: Figma de Camilo (agregar el enlace aquí).
