<div align="center">

# Bubbl.

**Leitor de mangás e quadrinhos para Android — toque no balão, zoom na hora.**

[![CI](https://github.com/walterfr/bubbl/actions/workflows/android.yml/badge.svg)](https://github.com/walterfr/bubbl/actions/workflows/android.yml)
![minSdk](https://img.shields.io/badge/minSdk-26-blue)
![Kotlin](https://img.shields.io/badge/Kotlin-2.1-7F52FF)
![License](https://img.shields.io/badge/license-MIT-green)

<img src="docs/img/home.png" width="640" alt="Tela inicial do Bubbl."/>

</div>

---

## O que é

Bubbl. abre arquivos de quadrinho/mangá e deixa a leitura fluida numa ideia central: **um toque na página dá zoom automático ancorado no ponto tocado** — o balão de diálogo que você quer ler. Toca de novo, volta pro enquadramento da página. Sem menus, sem pinça.

## Recursos

- **Zoom no toque** ancorado no ponto (o balão), com animação suave.
- **Tiling de imagens grandes** — páginas de mangá em alta resolução sem estourar memória.
- **Leitura imersiva** — barras de sistema escondidas, chrome translúcido (voltar, direção, contador, slider de página).
- **Continuar de onde parou** — cada livro guarda a última página; a Home lista até 10 recentes em "Continuar lendo".
- **Direita→esquerda (mangá)** — alterna a direção de leitura por livro; o slider acompanha o sentido.
- **Abertura com progresso** — "Abrindo… 12 / 80" e botão Cancelar.
- **Sobrevive à morte do processo** — o leitor recarrega o livro sozinho ao voltar do segundo plano.
- **Vários formatos** num pipeline único.
- **Material 3** (tema escuro), ícone adaptativo vetorial.

## Formatos suportados

| Formato | Como é lido |
|---|---|
| **CBZ / ZIP** | Imagens no arquivo (`java.util.zip`) |
| **CBR / RAR** | [junrar](https://github.com/junrar/junrar) |
| **PDF** | `PdfRenderer` nativo do Android (sem lib) |
| **EPUB** (fixed-layout) | Imagens do container, ordem por nome |
| **Imagens** | JPG · PNG · WEBP · GIF · BMP |
| **Pasta** | Seletor de árvore (SAF / `DocumentFile`) |

Tudo é convertido numa **lista única de páginas** (`Uri`), então o visualizador trata todo formato igual. Detalhes em [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

## Gestos e controles

| Gesto / controle | Ação |
|---|---|
| **Toque** | Amplia o balão sob o dedo (no formato dele) |
| **Toque duplo** | Zoom normal do documento |
| **Pinça** | Zoom livre |
| **Arrastar** | Pan (quando com zoom) |
| **Deslizar horizontal** | Trocar de página |
| **Slider inferior** | Pular para qualquer página (invertido em direita→esquerda) |
| **Botão de direção** (topo do leitor) | Alterna esquerda→direita / direita→esquerda (mangá), salvo por livro |
| **Toque em "Continuar lendo"** (Home) | Reabre o livro na última página |
| **Toque longo em "Continuar lendo"** | Remove da lista (com Desfazer) |
| **Cancelar** (ao abrir) | Interrompe a extração do livro |

## Build & Run

Requisitos: **JDK 17**, Android SDK (platform 36), um dispositivo/emulador **Android 8.0+ (API 26)**.

```bash
git clone https://github.com/walterfr/bubbl.git
cd bubbl
./gradlew installDebug     # compila e instala no dispositivo conectado
```

Só gerar o APK:

```bash
./gradlew assembleDebug    # app/build/outputs/apk/debug/app-debug.apk
```

Rodar os testes:

```bash
./gradlew test
```

> No Windows, use `gradlew.bat`. Ou abra a pasta no **Android Studio** (Meerkat+) e rode a config `app`.

## Stack

- **Kotlin** · Android Views · Material 3
- [SubsamplingScaleImageView](https://github.com/davemorrissey/subsampling-scale-image-view) — pan/zoom + tiling
- [OpenCV](https://opencv.org/) — contornos na detecção de balão
- ViewPager2 · Coroutines · DocumentFile
- `PdfRenderer` (nativo) · junrar (CBR)

## Roadmap

- [x] Zoom no balão: **toque único** isola o balão (flood-fill) e infla ~2x no seu formato; **toque duplo** dá o zoom normal do documento.
- [x] Detecção em cascata: flood-fill local → contornos OpenCV → flood-fill global.
- [ ] Detecção robusta de balão (modelo ML) para os casos que a heurística erra.
- [x] Direção de leitura direita→esquerda (mangá), por livro.
- [x] Continuar de onde parou + histórico de recentes ("Continuar lendo").
- [ ] Biblioteca completa / marcadores.
- [ ] Carga de páginas sob demanda (hoje extrai o livro inteiro pro cache ao abrir).
- [ ] EPUB baseado em texto (parse de OPF/spine + render HTML).

Veja limitações detalhadas em [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md#limitações-conhecidas).

## Contribuindo

PRs e issues são bem-vindos. Leia [CONTRIBUTING.md](CONTRIBUTING.md).

## Licença

[MIT](LICENSE) © Walter Rebouças
