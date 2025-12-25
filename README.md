[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Maven Central](https://img.shields.io/maven-central/v/com.colofabrix.scala/declinio_3.svg?label=Maven%20Central)](https://search.maven.org/search?q=g:%22com.colofabrix.scala%22%20AND%20a:%22declinio_3%22)

# Declinio

**Declinio** is a Scala 3 library that provides seamless integration between [Decline](https://ben.kirw.in/decline/) and [Cats Effect](https://typelevel.org/cats-effect/) avoiding the awkward constructor syntax of Decline.

It simplifies the creation of command-line applications by combining the declarative argument parsing of Decline with the powerful effect management of Cats Effect.

## Features

- **Simple Integration** - Combine Decline's elegant argument parsing with Cats Effect's IO monad
- **Minimal Boilerplate** - Just extend a trait and define your options
- **Configurable** - Support for custom version flags, help messages, and more
- **Type-Safe** - Leverage Scala's type system for compile-time safety
- **Effect-Aware** - Full support for Cats Effect's resource management and error handling

## Installation

Add the following dependencies to your `build.sbt`:

```scala
libraryDependencies ++= Seq(
  "com.colofabrix.scala" %% "declinio"    % "0.1.0",
  "com.monovore"         %% "decline"     % "2.4.1",  // Required peer dependency
  "org.typelevel"        %% "cats-effect" % "3.5.4",  // Required peer dependency
)
```

> **Note:** Declinio uses `Provided` scope for its dependencies, giving you full control over
> the versions of Decline and Cats Effect in your project.

## Quick Start

### Basic Application with Configuration

Create a command-line application that parses arguments:

```scala
import cats.effect.*
import com.colofabrix.scala.declinio.*
import com.monovore.decline.*

// Define your configuration case class
case class Config(name: String, count: Int, verbose: Boolean)

object MyApp extends IODeclineApp[Config]:

  override def name: String =
    "my-app"

  override def header: String =
    "A simple example application"

  override def version: String =
    "1.0.0"

  override def options: Opts[Config] =
    val nameOpt    = Opts.option[String]("name", help = "Your name", short = "n")
    val countOpt   = Opts.option[Int]("count", help = "Number of greetings", short = "c").withDefault(1)
    val verboseOpt = Opts.flag("verbose", help = "Enable verbose output", short = "v").orFalse
    (nameOpt, countOpt, verboseOpt).mapN(Config.apply)

  override def runWithConfig(config: Config): IO[ExitCode] =
    for {
      _ <- IO.println(s"Hello, ${config.name}!")
      _ <- if (config.verbose) IO.println(s"Greeting you ${config.count} times...") else IO.unit
      _ <- (1 until config.count).toList.traverse_(_ => IO.println(s"Hello again, ${config.name}!"))
    } yield ExitCode.Success
```

Run with:
```bash
sbt "run --name World --count 3 --verbose"
```

### Simple Application without Configuration

For applications that don't need command-line arguments:

```scala
import cats.effect.*
import com.colofabrix.scala.declinio.*

object SimpleApp extends IOUnitDeclineApp:

  override def name: String =
    "simple-app"

  override def header: String =
    "A simple application without arguments"

  override def version: String =
    "1.0.0"

  override def runNoConfig(): IO[ExitCode] =
    IO.println("Hello, World!").as(ExitCode.Success)
```

### Custom Effect Type

For applications using a custom effect type other than `IO`:

```scala
import cats.effect.*
import cats.effect.std.Console
import com.colofabrix.scala.declinio.*
import com.monovore.decline.*

// Example with a custom effect type (e.g., IO with environment)
case class AppConfig(debug: Boolean)

object CustomEffectApp extends IOApp with DeclineApp[IO, AppConfig]:

  override def name: String =
    "custom-app"

  override def header: String =
    "Application with custom effect"

  override def options: Opts[AppConfig] =
    Opts.flag("debug", "Enable debug mode", short = "d").orFalse.map(AppConfig.apply)

  override def runWithConfig(config: AppConfig): IO[ExitCode] =
    for {
      _ <- IO.println(s"Debug mode: ${config.debug}")
    } yield ExitCode.Success

  override def run(args: List[String]): IO[ExitCode] =
    runDeclineApp(args)
```

## API Reference

### DeclineApp[F, A]

The main trait that provides Decline integration for any effect type `F[_]` that supports `Sync` and `Console`.

| Member           | Type                          | Description                                          |
|------------------|-------------------------------|------------------------------------------------------|
| `name`           | `String`                      | Name of the application (required)                   |
| `header`         | `String`                      | Short description of the application (required)      |
| `version`        | `String`                      | Version of the application (optional, default: "")   |
| `options`        | `Opts[A]`                     | Decline command-line options (required)              |
| `helpFlag`       | `Boolean`                     | Display help on wrong arguments (default: true)      |
| `runWithConfig`  | `A => F[ExitCode]`            | Main application logic (required)                    |
| `runDeclineApp`  | `List[String] => F[ExitCode]` | Entry point to run the app                           |

### IODeclineApp[A]

A convenience trait that extends `IOApp` and `DeclineApp[IO, A]`. Use this for standard Cats Effect IO applications.

### IOUnitDeclineApp

A convenience trait for applications that don't need command-line arguments. Extends `IODeclineApp[Unit]`.

| Member        | Type           | Description                        |
|---------------|----------------|------------------------------------|
| `runNoConfig` | `IO[ExitCode]` | Main application logic (required)  |

## Advanced Usage

### Environment Variables

Declinio supports environment variables through Decline's native functionality:

```scala
override def options: Opts[Config] =
  val apiKey = Opts.env[String]("API_KEY", help = "API key for authentication")
  val debug  = Opts.flag("debug", "Enable debug mode").orFalse
  (apiKey, debug).mapN(Config.apply)
```

### Subcommands

Use Decline's subcommand support:

```scala
sealed trait Command
case class Add(item: String) extends Command
case class Remove(id: Int) extends Command

override def options: Opts[Command] =
  val add =
    Opts.subcommand("add", "Add a new item") {
      Opts.argument[String]("item").map(Add.apply)
    }

  val remove =
    Opts.subcommand("remove", "Remove an item") {
        Opts.argument[Int]("id").map(Remove.apply)
    }

  add orElse remove
```

### Error Handling

Leverage Cats Effect's error handling:

```scala
override def runWithConfig(config: Config): IO[ExitCode] =
  myProgram(config).handleErrorWith { error =>
    IO.errorln(s"Error: ${error.getMessage}").as(ExitCode.Error)
  }
```

### Resource Management

Use Cats Effect's resource management:

```scala
override def runWithConfig(config: Config): IO[ExitCode] =
  Resource
    .make(IO.println("Acquiring resource...") *> IO.pure("resource"))(
      _ => IO.println("Releasing resource...")
    )
    .use { resource =>
      IO.println(s"Using $resource").as(ExitCode.Success)
    }
```

## Building from Source

```bash
# Clone the repository
git clone https://github.com/ColOfAbRiX/declinio.git
cd declinio

# Compile
sbt compile

# Run tests
sbt test

# Generate documentation
sbt doc

# Publish locally
sbt publishLocal
```

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add some amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## License

Declinio is released under the MIT license. See [LICENSE](LICENSE) for details.

## Author

[Fabrizio Colonna](mailto:colofabrix@tin.it)

## See Also

- [Decline](https://ben.kirw.in/decline/) - Composable command-line parsing for Scala
- [Cats Effect](https://typelevel.org/cats-effect/) - The pure asynchronous runtime for Scala
