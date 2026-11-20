# Book Room web (iPhone y cualquier navegador)

La misma app Book Room (Kotlin + Jetpack Compose) compilada a WebAssembly con Compose
Multiplatform. Se abre en Safari y se instala en la pantalla de inicio como una app, sin App Store.
Es un proyecto aparte: la app Android sigue igual.

## Qué incluye

Todo lo de la app Android salvo **Escuchar** (narración) y **Convertir PDF**:
biblioteca con portadas, «Continúa leyendo», lectura normal paginada y RSVP, subrayados, notas,
marcadores, páginas de consulta, índice y búsqueda, estadísticas, cambio de portada desde catálogos,
diccionario (Wikcionario), imágenes con IA (Gemini), tienda, bloqueo con portada y sincronización
con Google Drive (la misma carpeta «ReadRiffle» que usa la app Android).

Diferencias obligadas por ser una web:

- **Importar libros**: el botón + abre la app Archivos (se pueden elegir varios EPUB a la vez). No
  existe la «carpeta vigilada».
- **Tienda**: las tiendas se abren en Safari; lo que descargues queda en Archivos y se importa con
  «Importar EPUB descargado». Una web no puede incrustar otras webs con su propio bloqueador.
- **Bloqueo con portada**: una web no puede pedir la huella o la cara del móvil, así que usa un PIN
  propio de Book Room (Ajustes › Bloqueo).
- **Drive**: el permiso de Google en una web dura una hora; al sincronizar después, la app hace una
  ida y vuelta rápida a Google para renovarlo.
- **Requisito**: iOS 18.2 o posterior (WebAssembly con recolector de basura).

Los libros y el progreso se guardan en el almacenamiento del navegador de ese dispositivo
(IndexedDB). Con la app instalada en la pantalla de inicio, iOS no lo borra solo; aun así, conectar
Drive sirve de copia.

## Publicarla

1. La carpeta `web/` (o, si compilas tú, `composeApp/build/dist/wasmJs/productionExecutable/`) es
   la web completa: súbela tal cual a cualquier hosting estático con HTTPS.
   - **Vercel**: arrastra la carpeta o `vercel deploy`; `vercel.json` ya pone los tipos y cachés.
   - **Apache / hosting propio** (p. ej. un subdominio de tu dominio): sube el contenido; el
     `.htaccess` ya declara el tipo `application/wasm`.
2. Abre la dirección en Safari del iPhone y sigue el aviso para «Añadir a pantalla de inicio».

## Conectar Google Drive

1. En Google Cloud Console, en el **mismo proyecto** que la app Android: *APIs y servicios ›
   Credenciales › Crear credenciales › ID de cliente de OAuth › Aplicación web*.
2. En «Orígenes de JavaScript autorizados» y en «URI de redireccionamiento autorizados» pon la
   dirección exacta de la web (la que muestra Book Room en Ajustes › Sincronización, por ejemplo
   `https://libros.tudominio.es/`).
3. Copia el ID de cliente y pégalo en Ajustes › Sincronización, o escríbelo una sola vez en
   `config.js` (`googleClientId`) antes de subir la web.
4. Si la pantalla de consentimiento está «en pruebas», tu cuenta tiene que estar como usuario de prueba.

Sin comprobar todavía: que el ID de cliente web vea los archivos que creó la app Android con el
permiso `drive.file`. Si al sincronizar la web no ve tu biblioteca, es por eso.

## Compilar

Requisitos: JDK 17+ (el wrapper de Gradle descarga el resto).

```
./gradlew :composeApp:wasmJsBrowserDistribution
```

Para probar en el ordenador: `./gradlew :composeApp:wasmJsBrowserDevelopmentRun`.

El código portado está en `composeApp/src/wasmJsMain/kotlin/com/readriffle/app/` (misma estructura
que la app Android). Lo propio de la web:

- `platform/`: archivos (IndexedDB), ZIP del EPUB, red, fechas, selector de archivos.
- `shims/`: equivalentes web de lo que en Android daban Coil, DataStore, `R.string`, `Font(R.font…)`
  y el botón atrás, para que las pantallas no cambien.
- `resources/`: `index.html`, puente con el navegador (`bridge.js`), service worker, manifest,
  iconos y el tutorial de instalación.
- `tools/gen_r.py`: genera `R.kt` a partir de `strings.xml`, drawables y fuentes de la app Android.
