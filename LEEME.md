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

## Versión de ordenador

Con la ventana a partir de 900 px de ancho la web cambia a un diseño propio de escritorio (no es la
del móvil estirada):

- **Barra lateral** fija con las cuatro secciones (pastilla que se desliza a la elegida), el estado
  de Drive y los atajos. Sustituye al dock flotante del móvil.
- **Biblioteca**: pestañas Leyendo/Leídos en la cabecera, «Continúa leyendo» con las cifras del día
  al lado, rejilla que se adapta al ancho y portadas que se elevan al pasar el ratón.
- **Ficha del libro y ficha de Leídos** a dos columnas (portada y acciones a la izquierda; nota,
  cita y notas de lectores a la derecha).
- **Lectura** centrada con ancho cómodo. Atajos: ← → / AvPág / Espacio pasan página; en RSVP,
  Espacio lee o pausa, ← → saltan palabras y ↑ ↓ cambian la velocidad. Esc vuelve.
- Notas, Tienda y Ajustes centrados con ancho máximo; cursor de mano en todo lo pulsable.

## Primera visita y sesión recordada

- La primera vez aparece un aviso (tarjeta centrada, no pantalla completa) que explica la
  sincronización y pregunta si se quiere. «Ahora no» no vuelve a preguntar (se puede activar luego
  en Ajustes › Sincronización).
- La web recuerda la cuenta conectada. Como Google da a las webs permisos de una hora, al volver a
  entrar con el permiso caducado la web lo renueva sola con una ida y vuelta a Google sin preguntar
  nada (como mucho una vez cada 20 minutos).
- Si Google rechaza la cuenta (no está dada de alta como usuario de prueba, web no registrada, etc.)
  el aviso vuelve a salir con el error y «contacta con el desarrollador». Si en `config.js` se
  rellena `contactEmail`, el mensaje dice «escribe a <correo>». Sin ID de cliente configurado, el
  aviso dice que la sincronización aún no está activada.

Diferencias obligadas por ser una web:

- **Importar libros**: el botón + abre la app Archivos (se pueden elegir varios EPUB a la vez). No
  existe la «carpeta vigilada».
- **Tienda**: las tiendas se abren en Safari; lo que descargues queda en Archivos y se importa con
  «Importar EPUB descargado». Una web no puede incrustar otras webs con su propio bloqueador.
- **Bloqueo con portada**: una web no puede pedir la huella o la cara del móvil, así que usa un PIN
  propio de Book Room (Ajustes › Bloqueo).
- **Drive**: el permiso de Google en una web dura una hora; la web lo renueva sola al abrirla (ida y
  vuelta rápida a Google, sin preguntar).
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
