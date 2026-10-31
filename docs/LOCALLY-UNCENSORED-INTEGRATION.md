# Evaluación de integración: Locally Uncensored

Repositorio de referencia: https://github.com/PurpleDoubleD/locally-uncensored
Rama upstream revisada: `master`
Licencia upstream: GNU AGPL-3.0-only.

## Decisión de arquitectura

No copiar el proyecto completo encima de Deepfake ni reemplazar la aplicación Kotlin existente sin una migración deliberada.

Deepfake actualmente es una aplicación Android Kotlin sencilla que envía imágenes a una API FastAPI para el intercambio de rostros. Locally Uncensored es un estudio de IA mucho más amplio: React 19 + TypeScript + Tauri v2 + backend Rust, con una app Android generada por Tauri. Su carpeta `mobile-client/` es un cliente remoto web para conectarse a un equipo que ejecuta LU; no es una biblioteca Android Kotlin independiente.

La app Android upstream empaqueta un WebView/Tauri y su motor local Rust. Trasplantar directamente sus archivos Android, Gradle o Activity a Deepfake no conservaría las interfaces ni las dependencias y probablemente rompería la compilación.

## Partes que pueden orientar una adaptación

- Arquitectura móvil Android: permisos, insets, ciclo de vida, WebView/Tauri y configuración de red.
- Experiencia de chat local y selección/gestión de modelos.
- Cliente remoto y emparejamiento con un equipo por red local.
- Voz: captura/transcripción y lectura de respuestas.
- Estructura de módulos y controles de permisos del agente.

Para mantener el requisito de una app Android nativa, estas funciones deben portarse o reimplementarse en Kotlin/Android, no incorporar la UI web completa sin aprobación.

## Obligaciones de licencia

El upstream declara AGPL-3.0-only. Si se copian o modifican componentes cubiertos y se distribuye la app, deben conservarse avisos de copyright y licencia, ofrecer el código fuente correspondiente bajo AGPL y cumplir los requisitos de la licencia para interacción por red cuando aplique. Revisar además las licencias de cada dependencia, modelo y motor antes de empaquetarlos. No asumir que los pesos de modelos tienen la misma licencia que el código.

## Alcance actual

Este documento es una evaluación de integración. No se han copiado archivos upstream ni se ha reemplazado el código de Deepfake. La rama de evaluación mantiene intacta la rama `main`.

## Fuentes técnicas consultadas

- README upstream: https://github.com/PurpleDoubleD/locally-uncensored/blob/master/README.md
- Licencia upstream: https://github.com/PurpleDoubleD/locally-uncensored/blob/master/LICENSE
- Cliente móvil remoto: https://github.com/PurpleDoubleD/locally-uncensored/tree/master/mobile-client
- Android generado por Tauri: https://github.com/PurpleDoubleD/locally-uncensored/tree/master/src-tauri/gen/android
