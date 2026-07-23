BEGIN {
  FS = "\t"
  OFS = "\t"
}

NR == FNR {
  if (FNR > 1) {
    before[$1] = $2
  }
  next
}

FNR == 1 {
  print "VARIABLE_NAME", "BEFORE", "AFTER", "DELTA"
  next
}

{
  previous = ($1 in before) ? before[$1] : 0
  print $1, previous, $2, $2 - previous
}
