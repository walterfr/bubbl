# Changelog

Formato baseado em [Keep a Changelog](https://keepachangelog.com/pt-BR/1.1.0/),
versionamento [SemVer](https://semver.org/lang/pt-BR/).

## [Não lançado]

### Adicionado
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
- CI (GitHub Actions): `assembleDebug` + `test`.

### Corrigido
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
