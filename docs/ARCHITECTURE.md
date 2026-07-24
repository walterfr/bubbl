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

## Zoom no toque (o recurso central)

Em `ReaderActivity`, cada página tem um `GestureDetector`:

1. `onSingleTapConfirmed` converte o ponto tocado em coordenada da imagem com
   `viewToSourceCoord(x, y)`.
2. Se já estiver com zoom, volta ao enquadramento (`minScale`); senão, dá zoom
   (`minScale * 2.8`) **centrado no ponto** via `animateScaleAndCenter`.

O `OnTouchListener` retorna `false`, então o `SubsamplingScaleImageView` ainda
processa pan/pinça/toque-duplo normalmente — o toque único apenas adiciona o
zoom inteligente por cima.

> Isto é **zoom no ponto tocado**, não detecção real do balão. Funciona em
> qualquer formato sem processamento prévio. Detecção de balão/painel de
> verdade (visão computacional ou metadados) é trabalho futuro.

## Limitações conhecidas

- **Zoom por ponto, não por balão.** Sem visão computacional/ACBF.
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
