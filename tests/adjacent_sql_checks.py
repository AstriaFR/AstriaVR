"""Adjacent selection uses actual production predicates and list order, on synthetic data only."""
from pathlib import Path
import re
import sqlite3

code = (Path(__file__).resolve().parents[1] / 'app/src/main/java/dev/astriavr/player/PlaylistStore.kt').read_text(encoding='utf-8-sig')
method = code.split('fun adjacent(', 1)[1].split('fun save(', 1)[0]
orders = re.search(r'val order = if \(forward\) "([^"]+)" else "([^"]+)"', method).groups()
predicates = re.search(r'val selection = if \(forward\) "([^"]+)"\s+else "([^"]+)"', method).groups()
db = sqlite3.connect(':memory:')
db.execute('CREATE TABLE videos (_id INTEGER PRIMARY KEY, sort_order INTEGER)')

def adjacent(current, direction):
    branch = 0 if direction > 0 else 1
    anchor = db.execute('SELECT sort_order FROM videos WHERE _id=?', (current,)).fetchone()
    result = None
    if anchor:
        result = db.execute(f'SELECT _id FROM videos WHERE {predicates[branch]} ORDER BY {orders[branch]} LIMIT 1', (anchor[0], anchor[0], current)).fetchone()
    if result is None:
        result = db.execute(f'SELECT _id FROM videos ORDER BY {orders[branch]} LIMIT 1').fetchone()
    return result[0] if result else None

assert adjacent(1, 1) is None and adjacent(1, -1) is None
db.execute('INSERT INTO videos VALUES(1, 0)')
assert adjacent(1, 1) == 1 and adjacent(1, -1) == 1
db.executemany('INSERT INTO videos VALUES(?, ?)', [(4, -2), (7, 0), (3, 5), (9, 9)])
for expected in ([4, 7, 1, 3, 9], [9, 4, 7, 1, 3]):
    for i, current in enumerate(expected):
        assert adjacent(current, 1) == expected[(i + 1) % len(expected)]
        assert adjacent(current, -1) == expected[(i - 1) % len(expected)]
    db.execute('UPDATE videos SET sort_order=-3 WHERE _id=9')
assert adjacent(99, 1) == 9 and adjacent(99, -1) == 3
print('Playlist adjacency passed: empty, single, tie order, reorder, both wraps, removed current.')
