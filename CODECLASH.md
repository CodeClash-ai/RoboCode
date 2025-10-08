# [CodeClash] RoboCode
This is the starter codebase for the RoboCode arena featured in CodeClash.

The code represented in this codebase comes from the following sources:
- https://github.com/robo-code/robocode: This is the main RoboCode repository. We found several alternatives, including https://github.com/robocode-dev. We elected to use the repository with the most stars.
    - Specifically, from the home page of the [website](https://robocode.sourceforge.io/), we follow the instructions and download the `.jar` installer from https://sourceforge.net/projects/robocode/files/robocode/1.10.0/robocode-1.10.0-setup.jar/download, which is the latest stable version as of June 2024. Upon downloading, the `.jar` installer creates a directory under the user's home directory called `robocode/`, which contains the original contents reflected in this codebase.
    - As indicated by the `robocode-dev` creators, the `robocode` repository is the original (written [here](https://robocode-dev.github.io/tank-royale/articles/tank-royale.html)). For the original iteration of CodeClash, we decided to stick with the original, but we may consider supporting the modernized version in the future.

The original content is quite comprehensive in what is included:
- The `battles/` directory provides several example battle configurations that can be run out the box.
- The `javadoc/` directory provides the API documentation for writing robots, game details, etc.
- The `robots/` directory is where the submission code lives (under `robots/custom/`) and also contains many example robots.

We then make several minor modifications to the original codebase:
- We move all non `robocode.*` root level scripts (e.g., `meleerumble.*`, `teamrumble.*`) to the `archive/` directory. We do not expect these scripts to be used in the CodeClash competition, although they are left in the codebase for reference.
- We remove the `battles/intro.battle` file, which does not run successfully in our testing (command: `./robocode.sh -battle battles/intro.battle -nodisplay -nosound -results results.txt`).
- We add a `CODECLASH.md` file (this file) to document these changes.

There are several other folders that are arguably unnecessary (e.g., `theme/`, `templates/`), but we leave them in for completeness and faithfulness to the original codebase.
