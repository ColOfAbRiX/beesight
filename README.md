# Beesight

**Beesight** is a Scala 3 command-line tool that automatically detects the key flight stages of a skydive from GPS
data recorded by [Flysight](https://flysight.ca/) and [Airlog One](https://airlog.one/) devices. It identifies
takeoff, freefall exit, canopy deployment, and landing points, then outputs trimmed CSV files, interactive HTML charts,
and batch summary reports.

Built on [fs2](https://fs2.io/), [Cats Effect](https://typelevel.org/cats-effect/), and
[Decline](https://ben.kirw.in/decline/), Beesight processes flight data as a streaming pipeline with parallel batch
support and optional GraalVM native image compilation.

## Features

- **Automatic Stage Detection** — Detects takeoff, freefall, canopy, and landing transitions from GPS telemetry
- **Streaming Pipeline** — Processes files as fs2 streams with constant memory usage
- **Parallel Batch Processing** — Processes multiple jump files concurrently
- **Multi-Format Support** — Reads Flysight CSV and Airlog One data formats (both plain and zipped)
- **Data Trimming** — Cuts output to retain only flight-relevant data points around the jump
- **Interactive Charts** — Generates HTML charts with altitude and velocity plots using Plotly
- **Summary Reports** — Produces a CSV summary of detected stages across all processed files
- **GraalVM Native Image** — Compiles to a standalone native binary for fast startup

## How It Works

Beesight uses a three-step detection algorithm for each flight stage transition:

| Step              | Description                                                                                         |
|-------------------|-----------------------------------------------------------------------------------------------------|
| **Trigger**       | Threshold-based checks on speed, acceleration, and altitude to identify candidate transition points |
| **Validation**    | Forward-looking check that confirms the trajectory after the trigger matches the expected behaviour |
| **Inflection**    | Backward-looking two-segment linear regression to pinpoint the exact transition point               |

The detection operates on a sliding window of data points. Data is preprocessed with median smoothing and spike
removal (interpolation-based despiking) before the detection algorithm runs. Each flight stage has its own handler
with stage-specific thresholds and validation logic.

### Detected Flight Phases

| Phase             | Description                               |
|-------------------|------------------------------------------ |
| `BeforeTakeoff`   | On the ground or preparing for the jump   |
| `Climbing`        | In the aircraft, gaining altitude         |
| `Freefall`        | After exit, in free fall                  |
| `UnderCanopy`     | After deployment, descending under canopy |
| `Landed`          | On the ground after landing               |

## Installation

### Prerequisites

- **JDK 21+** (GraalVM required for native image builds)
- **sbt 1.12+**

### Build from Source

```bash
# Compile
sbt compile

# Run directly
sbt run -- -i /path/to/flysight/data

# Run tests
sbt test
```

### Native Image Build

To build a standalone native binary using GraalVM:

```bash
bash build-native.sh
```

The binary will be placed in `dist/beesight.bin`.

## Usage

### Basic Usage

Process all CSV/ZIP files in a directory:

```bash
sbt "run -i /path/to/flysight/data"
```

Process a single file:

```bash
sbt "run -i /path/to/flysight/Jump\ 1234.zip"
```

Use glob patterns:

```bash
sbt "run -i /path/to/data/**/*.csv"
```

### CLI Options

| Option                    | Short | Description                                           | Default         |
|---------------------------|-------|-------------------------------------------------------|-----------------|
| `--input`                 | `-i`  | Input path (file, directory, or glob pattern)         | `.`             |
| `--output`                | `-o`  | Output directory for produced files                   | `../processed/` |
| `--limit`                 | `-l`  | Maximum number of files to process                    | No limit        |
| `--parallelism`           |       | Number of concurrent threads for batch processing     | No limit        |
| `--keep-before-takeoff`   |       | Number of data points to keep before detected takeoff | `500`           |
| `--keep-after-landing`    |       | Number of data points to keep after detected landing  | `500`           |
| `--min-retain-points`     |       | Minimum percentage of data points to retain (0.0–1.0) | `0.1`           |
| `--dry-run`               | `-n`  | Run without creating output files                     | `false`         |
| `--toggle-write-csv`      |       | Toggle CSV output writing                             | Enabled         |
| `--toggle-create-graph`   |       | Toggle HTML chart generation                          | Disabled        |
| `--toggle-create-summary` |       | Toggle summary CSV creation                           | Disabled        |

### Examples

Process with chart generation enabled:

```bash
sbt "run -i /path/to/data --toggle-create-graph --toggle-create-summary"
```

Process first 10 files with limited parallelism:

```bash
sbt "run -i /path/to/data -l 10 --parallelism 4"
```

Dry run to preview detection without writing files:

```bash
sbt "run -i /path/to/data -n"
```

## Output

### Processed CSV

Each input file produces a corresponding CSV in the output directory with an added `phase` column indicating the
detected flight phase for each data point. The CSV is trimmed to include only the jump-relevant portion of the data.

### HTML Chart

When enabled with `--toggle-create-graph`, an interactive HTML chart is generated for each file showing:

- Altitude over time
- Vertical velocity over time
- Detected stage transition markers

### Summary CSV

When enabled with `--toggle-create-summary`, a single `summary.csv` is generated containing the detected stage
points (index and altitude) for all processed files.

## Supported Data Formats

| Format        | File Types       | Description                                                   |
|---------------|------------------|---------------------------------------------------------------|
| **Flysight**  | `.csv`, `.zip`   | [Flysight GPS](https://flysight.ca/) data with velocity data  |
| **Airlog One**| `.csv`, `.zip`   | [Airlog One](https://airlog.one/) altimeter data              |

## Development

### Code Quality

```bash
# Apply formatting and linting fixes
sbt styleApply

# Check formatting and linting
sbt styleCheck
```

### Project Structure

```
src/main/scala/com/colofabrix/scala/beesight/
├── collections/       # NonEmptyWindow sliding window data structure
├── config/            # CLI and detection configuration
├── detection/         # Flight stage detection engine
│   ├── math/          # Interpolation, kinematics, statistics
│   ├── model/         # Detection state and data types
│   ├── preprocess/    # Data smoothing and despiking
│   └── stages/        # Per-phase detection handlers
├── input/             # CSV/ZIP file reading
├── model/             # Domain types (flight phases, events, vectors)
│   ├── derivation/    # CSV codec derivations
│   └── formats/       # File format adapters (Flysight, Airlog)
├── output/            # CSV writing, chart generation, result printing
└── utils/             # Stream utilities and helpers
```

## License

Beesight is released under the MIT license. See [LICENSE](LICENSE) for details.

## Author

[Fabrizio Colonna](mailto:colofabrix@tin.it)

## Documentation

- [Detection Algorithm](detection_algorithm.md) — Detailed description of how the flight stage detection works

## See Also

- [Flysight](https://flysight.ca/) — GPS logger for skydivers
- [Flysight File Format](https://flysight.ca/wiki/index.php/File_format) — CSV format documentation
- [Airlog One](https://airlog.one/) — Digital altimeter for skydivers
- [fs2](https://fs2.io/) — Functional streams for Scala
- [Cats Effect](https://typelevel.org/cats-effect/) — Pure asynchronous runtime for Scala
