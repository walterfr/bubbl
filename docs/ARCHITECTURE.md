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
   cresce uma região (flood-fill) a partir do ponto sobre pixels de brilho
   parecido até o contorno do balão, e devolve o bounding box. Rejeita quando
   vaza (fração/raio de preenchimento) — aí não era balão.
3. Achou o balão: recorta nítido com `BitmapRegionDecoder` e **mascara no formato
   da silhueta** (máscara alpha da região dilatada, via `PorterDuff.DST_IN`),
   posiciona o recorte **sobre onde o balão está** (`sourceToViewCoord`) e
   **infla ~2x no lugar** (anima `scaleX/scaleY`), com a página levemente
   escurecida atrás. Toque fecha.
4. Não achou: cai no **zoom no ponto** via `animateScaleAndCenter` (fallback).

O `OnTouchListener` retorna `false`, então o `SubsamplingScaleImageView` ainda
processa pan/pinça/toque-duplo normalmente.

> A detecção é heurística (flood-fill), boa em balão de interior uniforme com
> contorno fechado. Balão colorido/aberto/invertido pode não isolar → fallback.
> Upgrade: OpenCV (morfologia) ou modelo ML de detecção de balão.

## Limitações conhecidas

- **Detecção de balão é heurística.** Flood-fill isola balão de interior
  uniforme; casos difíceis caem no zoom no ponto. Robustez real = OpenCV/ML.
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
- **`minSdk` 26** para usar ícone adaptativo 100% vetorial (sem gerar PNGs).
