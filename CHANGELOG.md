# Changelog

Formato baseado em [Keep a Changelog](https://keepachangelog.com/pt-BR/1.1.0/),
versionamento [SemVer](https://semver.org/lang/pt-BR/).

## [Não lançado]

### Adicionado
- Leitor com **zoom automático no toque** ancorado no ponto (o balão), via
  `SubsamplingScaleImageView` com tiling de imagens grandes.
- Suporte a **CBZ/ZIP, CBR/RAR, PDF, EPUB (fixed-layout), imagens e pasta**,
  convergindo num pipeline único de páginas (`Uri`).
- Ordenação **natural** de páginas (1, 2, 10) com teste.
- **Home Material 3** (tema escuro) e **reader imersivo** com barra translúcida,
  contador e slider de página.
- **Logo/ícone** balão + lupa como ícone adaptativo vetorial (com monochrome
  para ícones temáticos do Android 13+).
- CI (GitHub Actions): `assembleDebug` + `test`.

### Notas
- `minSdk` 26 (Android 8.0) para usar ícone adaptativo vetorial puro.

[Não lançado]: https://github.com/walterfr/bubbl/commits/main
