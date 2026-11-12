# Contribuindo com o Bubbl.

Obrigado pelo interesse! Este guia é curto de propósito.

## Ambiente

- **JDK 17**, Android SDK com platform **android-36**.
- Android Studio Meerkat+ (recomendado) ou só o Gradle wrapper.

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
4. Rode `./gradlew test lintDebug` antes de abrir o PR. A CI roda `test`,
   `lintDebug` (erro de lint quebra o build), `assembleDebug` e
   `assembleRelease` (R8, sem assinatura).
5. Abra o PR contra `main` descrevendo **o quê** e **por quê**.

## Estilo de código

- Kotlin oficial (`kotlin.code.style=official`).
- Mudanças cirúrgicas: toque só no necessário, mantenha o estilo do arquivo.
- Lógica não-trivial (parser, gesto, ordenação) leva um teste. Veja
  [`NaturalOrderTest.kt`](app/src/test/java/com/bubbl/reader/NaturalOrderTest.kt).

## Release

O APK de release é assinado na CI pelo workflow
[`release.yml`](.github/workflows/release.yml), disparado por tags `v*`.
Sem os secrets, `./gradlew assembleRelease` ainda funciona e gera um APK
**não assinado** (é o que a CI de PR faz para pegar erros de R8).

Configuração (uma vez só):

1. Gere o keystore e **guarde-o fora do repositório** (com backup — sem ele
   não dá para atualizar o app instalado):

   ```bash
   keytool -genkeypair -v -keystore bubbl.jks -keyalg RSA -keysize 2048 -validity 10000 -alias bubbl
   ```

2. Em *Settings → Secrets and variables → Actions*, cadastre:

   | Secret | Valor |
   |---|---|
   | `BUBBL_KEYSTORE_BASE64` | saída de `base64 -w0 bubbl.jks` |
   | `BUBBL_KEYSTORE_PASSWORD` | senha do keystore |
   | `BUBBL_KEY_ALIAS` | alias da chave (ex.: `bubbl`) |
   | `BUBBL_KEY_PASSWORD` | senha da chave |

3. **Nunca commite o keystore** (`*.jks` e `*.keystore` estão no `.gitignore`).

Para publicar: atualize `versionCode`/`versionName` em
[`app/build.gradle.kts`](app/build.gradle.kts) e o [CHANGELOG](CHANGELOG.md),
faça merge na `main` e crie a tag:

```bash
git tag v0.2.0 && git push origin v0.2.0
```

O workflow roda os testes, gera o APK assinado e publica uma GitHub Release
com `bubbl-v0.2.0.apk`.

Para assinar localmente, exporte as mesmas variáveis (`BUBBL_KEYSTORE_PATH`
apontando para o `.jks`, `BUBBL_KEYSTORE_PASSWORD`, `BUBBL_KEY_ALIAS`,
`BUBBL_KEY_PASSWORD`) antes de `./gradlew assembleRelease`.

## Onde mexer

- **Formatos / carregamento:** [`PageLoader.kt`](app/src/main/java/com/bubbl/reader/PageLoader.kt)
- **Detecção de balão:** [`BalloonDetector.kt`](app/src/main/java/com/bubbl/reader/BalloonDetector.kt)
- **Zoom / gestos / reader:** [`ReaderActivity.kt`](app/src/main/java/com/bubbl/reader/ReaderActivity.kt)
- **Home / abertura:** [`MainActivity.kt`](app/src/main/java/com/bubbl/reader/MainActivity.kt)

Contexto de arquitetura: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).
