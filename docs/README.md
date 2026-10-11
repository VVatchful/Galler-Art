# Developer documentation

- [Architecture and component ownership](architecture.md)
- [ASCII conversion pipeline](ascii-pipeline.md)
- [Video decode and prefetch flow](video-flow.md)
- [Reproducing the example screenshots](examples.md)

## JavaDoc

Run from the project root with JDK 11 or newer:

```powershell
javadoc -quiet -Xdoclint:all,-missing -d out/javadoc src/*.java tests/*.java
```

Open `out/javadoc/index.html` locally. Generated API pages remain in the ignored
build directory. Public production types, methods, constructors, and executable
test entry points carry JavaDoc comments. `-missing` suppresses warnings for
compiler-provided members and enum constants; syntax and references are checked.

Architecture diagrams use Mermaid and render in compatible Markdown viewers.
The prose beside each diagram also describes the flow when Mermaid is unavailable.
