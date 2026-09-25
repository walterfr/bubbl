# Arquitetura

Bubbl. é enxuto de propósito: duas telas, um pipeline. Sem banco, sem DI, sem
camadas especulativas.

## Visão geral

```
MainActivity ──escolhe arquivo/pasta──▶ PageLoader ──List<Uri>──▶ BookHolder
                                                                      │
                                                                      ▼
                                                              ReaderActivity
                                                        (ViewPager2 de páginas)
                                                                      │
                                                                      ▼
                                                     SubsamplingScaleImageView
                                                        (tiling + tap-to-zoom)
```

## Componentes

| Arquivo | Papel |
|---|---|
| [`MainActivity.kt`](../app/src/main/java/com/bubbl/reader/MainActivity.kt) | Home. Abre arquivo (SAF `OpenDocument`) ou pasta (`OpenDocumentTree`). Carrega em `Dispatchers.IO`. |
| [`PageLoader.kt`](../app/src/main/java/com/bubbl/reader/PageLoader.kt) | Converte qualquer entrada numa lista ordenada de `Uri` de página. |
| [`BookHolder.kt`](../app/src/main/java/com/bubbl/reader/BookHolder.kt) | Singleton que passa as páginas entre Activities sem estourar o tamanho do Intent. |
| [`ReaderActivity.kt`](../app/src/main/java/com/bubbl/reader/ReaderActivity.kt) | ViewPager2 de páginas, modo imersivo, chrome (voltar/slider) e o gesto de zoom. |

## Pipeline de páginas

A decisão central: **todo formato vira uma `List<Uri>` de imagens**, então o
visualizador é único e burro. Estratégia por formato:

- **CBZ/ZIP/EPUB** — `ZipInputStream`, extrai entradas de imagem pro `cacheDir`.
- **CBR/RAR** — junrar `Archive.extractFile` pro `cacheDir`.
- **PDF** — `PdfRenderer` renderiza cada página (~2000px de largura) em PNG no cache.
- **Imagem única** — copiada pro cache.
- **Pasta** — `DocumentFile.listFiles()` filtra imagens; usa os `content://` direto.

Arquivos extraídos viram `file://` Uris, e o `SubsamplingScaleImageView` faz
**tiling** deles — essencial para páginas de mangá em alta resolução sem OOM.

### Ordenação natural

Páginas ordenam por nome com comparação natural (`1, 2, 10`, não `1, 10, 2`),
ignorando zeros à esquerda. Implementação e teste:
[`PageLoader.compareNatural`](../app/src/main/java/com/bubbl/reader/PageLoader.kt) ·
[`NaturalOrderTest.kt`](../app/src/test/java/com/bubbl/reader/NaturalOrderTest.kt).

## Zoom no balão (o recurso central)

Toque numa página em `ReaderActivity`:

1. `viewToSourceCoord` converte o toque em coordenada da imagem.
2. [`BalloonDetector`](../app/src/main/java/com/bubbl/reader/BalloonDetector.kt)
   acha o bounding box do balão em cascata, parando no primeiro que acertar:
   1. **flood-fill local** — cresce a região de brilho parecido num raio em
      torno do toque; rejeita se vazar (fração/área de preenchimento);
   2. **contornos OpenCV** — limiar adaptativo + fechamento morfológico +
      `findContours`, considera apenas contornos que contêm o ponto tocado e
      escolhe o mais cheio/próximo. Se a lib nativa não carregar, este passo é
      pulado;
   3. **flood-fill global** — sem limite de raio.

   A silhueta sai de um flood-fill **restrito ao bbox** encontrado
   (`regionMask`); se ficar esparsa (toque no texto, contorno do OpenCV), usa o
   bbox inteiro — a máscara nunca sai vazia.
3. Achou o balão: recorta nítido com `BitmapRegionDecoder` e **mascara no formato
   da silhueta** (máscara alpha da região dilatada, via `PorterDuff.DST_IN`),
   posiciona o recorte **sobre onde o balão está** (`sourceToViewCoord`) e
   **infla ~2x no lugar** (anima `scaleX/scaleY`), com a página levemente
   escurecida atrás. Toque fecha.
4. Não achou balão: o toque único não faz nada.

**Toque único** só amplia balão; **toque duplo** dá o zoom normal do documento
(`SubsamplingScaleImageView`) — separados de propósito, pra não confundir. O
`OnTouchListener` retorna `false`, então o SSIV ainda processa pan/pinça/duplo-toque.

> A detecção é heurística (flood-fill + contornos OpenCV), boa em balão de
> interior uniforme com contorno fechado. Balão colorido/aberto/invertido pode
> não isolar → o toque não faz nada. Upgrade: modelo ML de detecção de balão.

## Limitações conhecidas

- **Detecção de balão é heurística.** Flood-fill + contornos OpenCV isolam
  balão de interior uniforme; casos difíceis não detectam (use o toque duplo).
  Robustez real = modelo ML.
- **OpenCV pesa no APK.** O release filtra para `arm64-v8a`/`armeabi-v7a`; o
  debug leva todos os ABIs (emulador x86_64). Em ABI sem lib, a detecção segue
  sem o passo OpenCV.
- **Extração antecipada.** O livro inteiro é extraído pro `cacheDir` ao abrir —
  simples e robusto, mas usa disco e demora em livros grandes. Trocar por carga
  sob demanda por página se virar gargalo.
- **EPUB** é tratado como zip de imagens (ordem por nome); EPUB de quadrinho
  baseado em texto (HTML) não é renderizado.
- **Direção fixa** esquerda→direita (mangá costuma ser direita→esquerda).
- **Um livro por vez.** Sem biblioteca, histórico ou marcadores.
- **Conflito de swipe:** com a página ampliada, o arraste horizontal faz pan (não
  troca de página) até voltar ao enquadramento — comportamento esperado.

## Decisões

- **Views, não Compose.** O app é essencialmente o reader (uma `View` de
  terceiros); um rewrite em Compose não se paga. Material 3 via
  Material Components já entrega o visual atual.
- **`PdfRenderer` nativo** em vez de lib de PDF — zero dependência.
- **OpenCV como reforço, não requisito.** Só entra como 2º passo da detecção;
  se a lib nativa falhar ao carregar, o app segue só com flood-fill.
- **`minSdk` 26** para usar ícone adaptativo 100% vetorial (sem gerar PNGs).
