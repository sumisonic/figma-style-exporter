# figma-style-exporter

Figma Style Exporter: A tool to extract text styles and color styles from Figma design systems and export them as JSON format.

## Overview

This tool retrieves style information from Figma files, converts it to CSS-compatible format, and outputs it as structured JSON. It supports both text styles and color styles.

## Features

- Text style extraction and JSON conversion
- Color style (fill) extraction and JSON conversion
- Breakpoint and responsive style support
- Multilingual support (BCP 47 language tags)
- Style structure and consistency validation
- File output or console output

## Requirements

- Figma API token
- Figma file ID
- Java runtime environment (JRE 8 or higher, Java 11 or higher recommended)

## Usage

The `.jar` file built by GitHub Actions can be downloaded from the releases page and executed.

[GitHub Releases](https://github.com/sumisonic/figma-style-exporter/releases)

```sh
# Basic usage
java -jar figma-style-exporter.jar text -o text.json

# When requiring breakpoints and languages
java -jar figma-style-exporter.jar text -B -L -o text.json

# When targeting specific breakpoints and languages
# Note: The -B -L flags must also be used
java -jar figma-style-exporter.jar text -B -L -b sm,md -l en,ja -o text.json
```

## Usage Instructions

### Setting Environment Variables

```sh
export FIGMA_TOKEN="your-figma-api-token"
export FIGMA_FILE_KEY="your-figma-file-id"
```

These environment variables are necessary for accessing the Figma API.

### Exporting Text Styles

```sh
java -jar figma-style-exporter.jar text [options]
```

**Options**
- `-b, --breakpoints BP1,BP2,...` – Comma-separated list of breakpoints (e.g. `sm,md,lg,xl`)
- `-l, --languages LANG1,LANG2,...` – Comma-separated list of languages (e.g. `en,ja,zh`)
- `-B, --require-breakpoint` – Require breakpoints in style names
- `-L, --require-language` – Require languages in style names
- `-o, --output PATH` – Output file path (outputs to stdout if not specified)
- `-h, --help` – Display help

### Exporting Color Styles

```sh
java -jar figma-style-exporter.jar color [options]
```

**Options**
- `-o, --output PATH` – Output file path (outputs to stdout if not specified)
- `-h, --help` – Display help

## Output Format

### Text Styles

```json
{
  "styleName": {
    "language": {      // "all" or BCP 47 language tag
      "property": {
        "breakpoint": value  // sm, md, lg, xl
      }
    }
  }
}
```

### Color Styles

```json
{
  "styleName": {
    "color": {
      "r": red value (0-1),
      "g": green value (0-1),
      "b": blue value (0-1),
      "a": alpha value (0-1)
    }
  }
}
```

## Text Style Naming Conventions

Figma text styles are named by combining the following three elements in a hierarchical structure:

### Required Elements

1. **Style Name**: The basic name of the style (e.g. `heading-1`, `body`, `caption`)
2. **Breakpoint**: Responsive design screen size (`sm`, `md`, `lg`, `xl`)

### Optional Elements

3. **Language Code**: BCP 47 format language tag (e.g. `ja`, `en`)
   - Use `all` for cases where no language is specified or the style is common to all languages

### Setup in Figma

In Figma's style panel, use a hierarchical structure (folder structure) to organize styles.
For example, set up with the following hierarchy:

```
heading-1
  └─ sm
      ├─ ja
      └─ en
body
  └─ lg
      └─ all
caption
  └─ xl
```

This structure can be validated using the `-B` and `-L` options. When these options are specified, the tool validates whether breakpoints and languages are included in the style names. You can also extract only specific language codes using the `-l` option (when using the `-l` option, you must also specify the `-B -L` flags).

⚠️ **Important**: The order of the hierarchy must be consistent within a project.
Standardize on one of the following patterns:

- Pattern 1: "StyleName → Breakpoint → Language"
- Pattern 2: "Language → Breakpoint → StyleName"

The selected pattern must be consistent across all text styles in your project.
If patterns are mixed, an error will occur during export.

### Concrete Examples

✅ Correct example (Pattern 1):
```
heading-1
  └─ sm
      └─ ja     # Heading 1, mobile size, Japanese
heading-2
  └─ md
      └─ en     # Heading 2, tablet size, English
body
  └─ lg
      └─ all    # Body text, desktop size, common to all languages
```

✅ Correct example (Pattern 2):
```
ja
  └─ sm
      └─ title  # Japanese, mobile size, title
en
  └─ lg
      └─ body   # English, desktop size, body text
all
  └─ xl
      └─ caption # All languages, large screen size, caption
```

❌ Example to avoid:
```
heading-1        # Pattern 1
  └─ sm
      └─ ja

ja              # Pattern 2
  └─ sm
      └─ title

# Mixing different patterns as shown above will result in an error
```

## License

This project is published under the [MIT License](LICENSE).
