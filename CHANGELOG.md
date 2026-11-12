# Changelog

Formato baseado em [Keep a Changelog](https://keepachangelog.com/pt-BR/1.1.0/),
versionamento [SemVer](https://semver.org/lang/pt-BR/).

## [Não lançado]

### Adicionado
- **Continuar de onde parou**: a última página de cada livro é salva (ao trocar
  de página e no `onPause`) e o reader reabre nela.
- **"Continuar lendo"** na Home: até 10 livros recentes com título e
  "página x de N"; toque reabre, toque longo remove (com Desfazer); livro que
  não abre mais oferece remoção.
- **Direção direita→esquerda (mangá)** por livro, com botão no chrome superior;
  slider invertido em RTL.
- **Progresso ao abrir** ("Abrindo… 12 / 80" ou "12 páginas") e botão
  **Cancelar**.
- Persistência do estado de leitura (`ReadingStore`, SharedPreferences + JSON)
  e permissão persistente do Uri escolhido.
- Testes JVM da lista de recentes e do mapeamento do slider em RTL.
- **Zoom no balão**: o toque isola o balão sob o dedo (flood-fill), recorta
  **no formato da silhueta do balão** (máscara alpha) e o **infla ~2x no lugar**
  sobre a página. Toque único só amplia balão; zoom do documento é no toque
  duplo.
- Detecção de balão em cascata: flood-fill local → contornos **OpenCV** →
  flood-fill global.
- Leitor com pan/zoom e tiling de imagens grandes via
  `SubsamplingScaleImageView`.
- Suporte a **CBZ/ZIP, CBR/RAR, PDF, EPUB (fixed-layout), imagens e pasta**,
  convergindo num pipeline único de páginas (`Uri`).
- Ordenação **natural** de páginas (1, 2, 10) com teste.
- **Home Material 3** (tema escuro) e **reader imersivo** com barra translúcida,
  contador e slider de página.
- **Logo/ícone** balão + lupa como ícone adaptativo vetorial (com monochrome
  para ícones temáticos do Android 13+).
- **Tela Sobre** com versão, links (GitHub, Instagram, contato) e apoio ao
  projeto: GitHub Sponsors, Ko-fi, Buy Me a Coffee e PIX copia-e-cola.
- CI (GitHub Actions): `test` + `lintDebug` + `assembleDebug`, com relatório
  de lint como artefato.
- **"Abrir com…"**: o Bubbl. aparece para CBZ, CBR, PDF e EPUB vindos de
  gerenciadores de arquivos e outros apps (`ACTION_VIEW`).
- Formato deduzido pelo **MIME** quando o arquivo recebido não tem extensão.
- **APK de release assinado**: workflow `release.yml` publica uma GitHub
  Release com `bubbl-vX.Y.Z.apk` a cada tag `v*` (keystore via secrets).
- Instruções de instalação (README) e de release (CONTRIBUTING).

### Alterado
- `PageLoader.load`/`loadFolder` agora são `suspend`, canceláveis e reportam
  progresso.
- A Home rola (`ScrollView`) para caber em telas pequenas.
- `compileSdk`/`targetSdk` 36, AGP 8.10, Kotlin 2.1, Gradle 8.11 e
  dependências AndroidX atualizadas.
- Telas em **edge-to-edge** (obrigatório no targetSdk 35+), respeitando barras
  do sistema e recorte da câmera.
- Release só com libs nativas ARM (`abiFilters`), reduzindo o APK.
- Removido o repositório JitPack (dependências vêm do Maven Central/Google).
- Versão **0.2.0** (`versionCode` 2).
- Release com **R8** (minify + shrink de recursos) e regras para OpenCV e
  junrar; a CI agora também roda `assembleRelease` em todo PR.

### Corrigido
- O reader não fecha mais ao voltar depois que o Android matou o processo:
  recarrega o livro a partir do Uri de origem.
- OpenCV não escolhe mais contornos próximos cujo bbox apenas contém o toque;
  o ponto precisa estar dentro do próprio contorno.
- O raio da busca local agora usa `LOCAL_SEARCH_RADIUS_FACTOR`, evitando um
  limite duplicado e não aplicado.
- Balão ampliado aparecia **invisível** quando detectado pelo flood-fill local
  ou pelo OpenCV (a máscara da silhueta saía vazia). A silhueta agora é
  calculada dentro do bbox, qualquer que seja o detector; teste de regressão.
- Crash ao tocar num balão quando a lib nativa do OpenCV não carrega (o
  retorno de `initDebug()` era ignorado). Agora o passo OpenCV é pulado.
- Vazamento de memória nativa do OpenCV a cada toque (`hierarchy`, `kernel` e
  contornos não eram liberados).

### Notas
- `minSdk` 26 (Android 8.0) para usar ícone adaptativo vetorial puro.

[Não lançado]: https://github.com/walterfr/bubbl/commits/main
