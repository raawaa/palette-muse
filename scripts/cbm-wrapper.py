#!/usr/bin/env python3
"""
cbm-wrapper.py — Protocol converter for codebase-memory-mcp.

Reasonix sends standard MCP Content-Length framed messages,
but the codebase-memory-mcp server expects raw JSON-line (newline-delimited) protocol.
This wrapper sits between them and does the conversion.
"""

import subprocess
import sys
import os
import json

CBM_BIN = os.environ.get(
    "CBM_BIN",
    "/Users/yuwenjie/.local/bin/codebase-memory-mcp",
)
_DEBUG = os.environ.get("CBM_DEBUG")


def debug(*args):
    if _DEBUG:
        print("[cbm-wrapper]", *args, file=sys.stderr, flush=True)


def read_content_length_frame():
    """Read one Content-Length framed message from stdin.
    Returns the JSON body as bytes, or None on EOF."""
    buf = b""
    while True:
        byte = sys.stdin.buffer.read(1)
        if not byte:
            return None
        buf += byte
        if buf.endswith(b"\r\n\r\n"):
            header = buf[:-4].decode("utf-8", errors="replace")
            content_length = 0
            for line in header.split("\r\n"):
                if line.lower().startswith("content-length:"):
                    content_length = int(line.split(":", 1)[1].strip())
                    break
            if content_length <= 0:
                debug("invalid content-length in header:", header)
                buf = b""
                continue
            body = sys.stdin.buffer.read(content_length)
            if len(body) != content_length:
                debug(f"expected {content_length} bytes, got {len(body)}")
                return None
            return body


def write_content_length_frame(data: bytes):
    """Write a Content-Length framed message to stdout."""
    header = f"Content-Length: {len(data)}\r\n\r\n".encode("utf-8")
    sys.stdout.buffer.write(header + data)
    sys.stdout.buffer.flush()


def main():
    debug("starting, cbm_bin=%s", CBM_BIN)

    proc = subprocess.Popen(
        [CBM_BIN] + sys.argv[1:],
        stdin=subprocess.PIPE,
        stdout=subprocess.PIPE,
        stderr=sys.stderr,
    )
    debug("child pid=%d", proc.pid)

    try:
        while True:
            # Read Content-Length framed request from Reasonix
            body = read_content_length_frame()
            if body is None:
                debug("stdin closed, exiting")
                break

            debug("received %d bytes from Reasonix", len(body))
            debug("request: %s", body[:200].decode("utf-8", errors="replace"))

            # Send as raw JSON line to the child
            proc.stdin.write(body + b"\n")
            proc.stdin.flush()
            debug("forwarded to child")

            # Read raw JSON line response from child
            response_line = b""
            while True:
                byte = proc.stdout.read(1)
                if not byte:
                    debug("child stdout closed")
                    raise BrokenPipeError("child process closed stdout")
                response_line += byte
                if byte == b"\n":
                    break

            # Strip trailing newline
            response_line = response_line.rstrip(b"\n\r")
            debug("response from child: %d bytes", len(response_line))

            # Write Content-Length framed response back to Reasonix
            write_content_length_frame(response_line)
            debug("response forwarded to Reasonix")

            # Check if child is still alive
            if proc.poll() is not None:
                debug("child process exited with code %d", proc.returncode)
                break
    except BrokenPipeError:
        debug("broken pipe, exiting")
    except KeyboardInterrupt:
        debug("interrupted, exiting")
    finally:
        proc.terminate()
        try:
            proc.wait(timeout=5)
        except subprocess.TimeoutExpired:
            proc.kill()
            proc.wait()


if __name__ == "__main__":
    main()
