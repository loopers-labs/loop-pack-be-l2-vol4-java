#!/bin/sh

set -eu

fail() {
    printf '%s\n' "$1" >&2
    exit 1
}

resolve_aggregation_end_date() {
    requested_date="${REQUESTED_AGGREGATION_END_DATE:-}"

    if [ -z "$requested_date" ]; then
        scheduled_millis="${BUILD_SCHEDULED_TIME_MILLIS:-}"
        case "$scheduled_millis" in
            ''|*[!0-9]*)
                fail 'BUILD_SCHEDULED_TIME_MILLIS must be a positive integer'
                ;;
        esac

        previous_day_seconds=$((scheduled_millis / 1000 - 86400))
        TZ=KST-9 date --date="@${previous_day_seconds}" '+%Y%m%d'
        return
    fi

    case "$requested_date" in
        [0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9])
            ;;
        *)
            fail 'AGGREGATION_END_DATE must be a valid yyyyMMdd date'
            ;;
    esac

    year="$(printf '%s' "$requested_date" | cut -c 1-4)"
    month="$(printf '%s' "$requested_date" | cut -c 5-6)"
    day="$(printf '%s' "$requested_date" | cut -c 7-8)"
    if ! resolved_date="$(
        TZ=KST-9 date \
            --date="${year}-${month}-${day}" \
            '+%Y%m%d' 2>/dev/null
    )"
    then
        fail 'AGGREGATION_END_DATE must be a valid yyyyMMdd date'
    fi

    if [ "$resolved_date" != "$requested_date" ]; then
        fail 'AGGREGATION_END_DATE must be a valid yyyyMMdd date'
    fi
    printf '%s\n' "$resolved_date"
}

resolve_revision() {
    requested_revision="${REQUESTED_REVISION:-}"
    case "$requested_revision" in
        ''|*[!0-9]*|0*)
            fail 'REVISION must be a positive integer'
            ;;
    esac

    if [ "${#requested_revision}" -gt 10 ] ||
        [ "$requested_revision" -gt 2147483647 ]
    then
        fail 'REVISION must not exceed 2147483647'
    fi
    printf '%s\n' "$requested_revision"
}

case "${1:-}" in
    aggregation-end-date)
        resolve_aggregation_end_date
        ;;
    revision)
        resolve_revision
        ;;
    *)
        fail 'usage: resolve-ranking-parameters.sh <aggregation-end-date|revision>'
        ;;
esac
