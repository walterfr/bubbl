# Bubbl — Comic & Manga Reader

Leitor Android (Kotlin). Toque na página = zoom automático ancorado no ponto tocado (o "balão"). Toque de novo = volta.

## Formatos
- **CBZ / ZIP** — imagens em arquivo (java.util.zip)
- **CBR / RAR** — via junrar
- **PDF** — PdfRenderer nativo do Android
- **EPUB (fixed-layout)** — extrai imagens do container (aproxima ordem por nome)
- **Imagens soltas** — jpg/png/webp/gif/bmp
- **Pasta** — seletor de árvore (SAF)

## Como rodar
1. Abrir a pasta no **Android Studio** (Hedgehog+). Ele gera o `gradlew` e baixa o SDK.
2. Rodar `:app` num emulador/dispositivo (minSdk 24).
3. Botão **Abrir arquivo** ou **Abrir pasta** → ler.

Teste da ordenação de páginas: `./gradlew test`.

## Como funciona o zoom
`SubsamplingScaleImageView` faz tiling de páginas grandes. Um `GestureDetector`
captura o toque único e chama `animateScaleAndCenter` no ponto tocado
convertido pra coordenada da imagem. Sem IA, funciona em qualquer formato.

## Limitações atuais (MVP) e próximos passos
- **Zoom por ponto, não por balão real.** Detecção de balão/painel por visão
  computacional (OpenCV/YOLO) fica pra depois. Alternativa boa: ler painéis
  pré-mapeados (ACBF) quando existirem.
- **PDF/arquivos são extraídos inteiros pro cache ao abrir** — simples e robusto,
  mas usa disco e demora em livros grandes. Trocar por carga sob demanda por página
  se virar gargalo.
- **EPUB** trata como zip de imagens (ordem por nome). Parse do OPF/spine e
  render de HTML só se aparecer EPUB de quadrinho baseado em texto.
- **Direção de leitura fixa (esquerda→direita).** Mangá costuma ser
  direita→esquerda — adicionar toggle (RTL no ViewPager2).
- Sem biblioteca/histórico/marcadores. Abre um livro por vez.
