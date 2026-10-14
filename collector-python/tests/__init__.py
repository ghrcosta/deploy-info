# Marks this directory as a package so both `python3 -m unittest discover` (from the project
# root) and `python3 -m unittest discover -s tests` work. Also puts this directory on sys.path
# so the test modules' `import _support` resolves in both modes.
import sys
from pathlib import Path

_here = str(Path(__file__).resolve().parent)
if _here not in sys.path:
    sys.path.insert(0, _here)
