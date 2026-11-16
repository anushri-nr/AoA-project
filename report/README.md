# Part I report: single LaTeX source

`main.tex` is the only LaTeX source file needed to compile the report. It includes the complete mathematical report, experimental results, IEEE-style references, Appendix A (the complete Java implementation), and Appendix B (AI tools, prompts, and results). `main.pdf` is the compiled 12-page report.

The six external figure PDFs are stored in `figures/` beside the source. No other `.tex`, `.bib`, or Java file is needed to compile it.

The layout follows the supplied AoA-Project.pdf: IEEE two-column formatting, an introduction, a main Problem 1 section with lettered subsections, an explanation in domain terms, references, implementation appendix, and prompt/result disclosure. It covers only the Cloud Task Assignment part and preserves all proofs, measurements, and code.

## Compile

From this folder, run either:

```sh
tectonic --keep-logs main.tex
```

or, with a suitable TeX distribution:

```sh
pdflatex -interaction=nonstopmode -halt-on-error main.tex
pdflatex -interaction=nonstopmode -halt-on-error main.tex
```

Required packages are declared in `main.tex`; the document class is IEEEtran. There is no BibTeX step. For Overleaf, upload `main.tex` and the `figures/` folder, or upload `latex-report.zip` directly.

## Editing and sharing

Make all report edits in `main.tex`. The former report fragments have been consolidated into it. The Java listing is now an embedded snapshot: if the implementation changes, update the corresponding listing before recompiling. The Python analysis and original experimental-results fragment remain outside this folder as supporting project files, but are not compilation dependencies.

`latex-report.zip` contains the single source, six figure PDFs, this README, and the compiled PDF. The original benchmark measurements and graphs have not been rerun or changed.

The report was built with Tectonic 0.17.0. Compilation has no unresolved references or overfull boxes; underfull-spacing and font fallback warnings remain. Rendered output has been visually checked.
