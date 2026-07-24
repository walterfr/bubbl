# Contribuindo com o Bubbl.

Obrigado pelo interesse! Este guia é curto de propósito.

## Ambiente

- **JDK 17**, Android SDK com platform **android-34**.
- Android Studio Hedgehog+ (recomendado) ou só o Gradle wrapper.

```bash
git clone https://github.com/walterfr/bubbl.git
cd bubbl
./gradlew assembleDebug
./gradlew test
```

## Fluxo

1. Abra uma **issue** antes de PRs grandes — evita retrabalho.
2. Branch a partir de `main`: `git checkout -b feat/minha-coisa`.
3. Commits no estilo [Conventional Commits](https://www.conventionalcommits.org/):
   `feat:`, `fix:`, `docs:`, `refactor:`, `chore:`, `test:`.
4. Rode `./gradlew test` antes de abrir o PR. A CI roda `assembleDebug` + `test`.
5. Abra o PR contra `main` descrevendo **o quê** e **por quê**.

## Estilo de código

- Kotlin oficial (`kotlin.code.style=official`).
- Mudanças cirúrgicas: toque só no necessário, mantenha o estilo do arquivo.
- Lógica não-trivial (parser, gesto, ordenação) leva um teste. Veja
  [`NaturalOrderTest.kt`](app/src/test/java/com/bubbl/reader/NaturalOrderTest.kt).

## Onde mexer

- **Formatos / carregamento:** [`PageLoader.kt`](app/src/main/java/com/bubbl/reader/PageLoader.kt)
- **Zoom / gestos / reader:** [`ReaderActivity.kt`](app/src/main/java/com/bubbl/reader/ReaderActivity.kt)
- **Home / abertura:** [`MainActivity.kt`](app/src/main/java/com/bubbl/reader/MainActivity.kt)

Contexto de arquitetura: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).
