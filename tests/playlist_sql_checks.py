"""Offline SQL checks on temporary data only; no Android device or personal database access."""
from pathlib import Path
import random
import re
import sqlite3
import tempfile

root = Path(__file__).resolve().parents[1]
code = (root / "app/src/main/java/dev/astriavr/player/PlaylistStore.kt").read_text(encoding="utf-8-sig")
sql = re.findall(r'(?:db|writableDatabase)\.execSQL\("([^"]+)"', code)
one = lambda prefix: next(statement for statement in sql if statement.startswith(prefix))
legacy = "CREATE TABLE videos (_id INTEGER PRIMARY KEY AUTOINCREMENT, uri TEXT NOT NULL UNIQUE, name TEXT NOT NULL, position INTEGER NOT NULL DEFAULT 0, duration INTEGER NOT NULL DEFAULT 0, layout INTEGER NOT NULL DEFAULT -1, projection INTEGER NOT NULL DEFAULT 0, revision INTEGER NOT NULL DEFAULT 0)"

# Read the destination expressions from production code rather than restating their arithmetic.
destinations = re.findall(r'destination = if \(after\) ([^\n]+) else ([^\n]+)', code)
assert len(destinations) == 2
checks = 0
def check(condition, message):
    global checks
    checks += 1
    assert condition, message

with tempfile.TemporaryDirectory(prefix="astriavr-sql-") as folder:
    path = Path(folder) / "playlist.db"
    db = sqlite3.connect(path)
    db.execute(legacy)
    for i in range(1, 61):
        db.execute("INSERT INTO videos(uri,name,position,duration,revision) VALUES(?,?,?,?,?)",
                   (f"content://sample/{i}", f"video {i}", i * 31, 600000, i % 3))
    db.execute("DELETE FROM videos WHERE _id IN (4,11,18,30,45)")
    original = db.execute("SELECT * FROM videos ORDER BY _id DESC").fetchall()
    with db:
        for statement in sql:
            if statement.startswith("ALTER TABLE"):
                db.execute(statement)
        db.execute(one("UPDATE videos SET sort_order=-"))
        db.execute(one("CREATE INDEX"))
    rows = db.execute("SELECT * FROM videos ORDER BY sort_order ASC, _id DESC").fetchall()
    check([row[:8] for row in rows] == original, "migration retains every old field and prior order")
    check(all(row[12] == int(row[7] > 0) for row in rows), "manual thumbnail selections survive migration")
    check(all(row[9] == -1 and row[10] == '' and row[11] == 0 for row in rows), "unknown metadata has explicit defaults")

    expected = [row[0] for row in rows]
    payload = {row[0]: row[:8] + row[9:] for row in rows}
    rng = random.Random(351)
    for _ in range(1200):
        source, anchor = rng.sample(expected, 2)
        after = bool(rng.randrange(2))
        from_order = db.execute("SELECT sort_order FROM videos WHERE _id=?", (source,)).fetchone()[0]
        target = db.execute("SELECT sort_order FROM videos WHERE _id=?", (anchor,)).fetchone()[0]
        branch = 0 if from_order < target else 1
        expression = destinations[branch][0 if after else 1].strip()
        destination = eval(expression, {"__builtins__": {}}, {"target": target})
        with db:
            if branch == 0:
                db.execute(one("UPDATE videos SET sort_order=sort_order-1"), (from_order, destination))
            else:
                db.execute(one("UPDATE videos SET sort_order=sort_order+1"), (destination, from_order))
            db.execute(one("UPDATE videos SET sort_order=?"), (destination, source))
        expected.remove(source)
        expected.insert(expected.index(anchor) + int(after), source)
        rows = db.execute("SELECT * FROM videos ORDER BY sort_order ASC, _id DESC").fetchall()
        check([row[0] for row in rows] == expected, "drop matches list insertion semantics")
        check(len({row[8] for row in rows}) == len(rows), "order keys remain distinct")
        check(all(row[:8] + row[9:] == payload[row[0]] for row in rows), "reorder changes no media metadata or progress")

    with db:
        db.execute(one("INSERT OR IGNORE INTO videos"), ("content://new", "new video"))
    new_id = db.execute("SELECT _id FROM videos WHERE uri='content://new'").fetchone()[0]
    expected.insert(0, new_id)
    with db:
        db.execute(one("INSERT OR IGNORE INTO videos"), ("content://new", "new video"))
    check([row[0] for row in db.execute("SELECT _id FROM videos ORDER BY sort_order ASC, _id DESC")] == expected,
          "new import goes first and duplicate import never reorders")
    db.close()
    db = sqlite3.connect(path)
    check([row[0] for row in db.execute("SELECT _id FROM videos ORDER BY sort_order ASC, _id DESC")] == expected,
          "ordering persists after reopening the database")
    db.close()
print(f"PASS: {checks} migration, ordering, metadata-preservation and reopen checks on isolated SQLite data")
