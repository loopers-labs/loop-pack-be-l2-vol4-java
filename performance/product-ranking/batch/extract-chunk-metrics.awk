BEGIN {
  FS = ","
  OFS = ","
  print "step_name", "commit_count", "duration_ms", "read_delta", "write_delta", "read_count", "write_count"
}

/PERF_CHUNK / {
  line = $0
  sub(/^.*PERF_CHUNK /, "", line)
  gsub(/, /, ",", line)

  field_count = split(line, fields, ",")
  delete values
  for (field_index = 1; field_index <= field_count; field_index++) {
    separator = index(fields[field_index], "=")
    if (separator > 0) {
      key = substr(fields[field_index], 1, separator - 1)
      value = substr(fields[field_index], separator + 1)
      values[key] = value
    }
  }

  print values["step"], values["commitCount"], values["durationMs"], values["readDelta"], values["writeDelta"], values["readCount"], values["writeCount"]
}
