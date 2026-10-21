#!/usr/bin/env bash
# Google TV profiles for "Who's watching?": each one is an Android profile with its own app data, so its
# own Netflix / Spotify / YouTube logins. The launcher opens apps inside the chosen person's profile.
#   tools/profiles.sh add Alex [debloat list]   create a profile, copy your apps in, apply the debloat list
#   tools/profiles.sh list                       show profiles and their user ids
#   tools/profiles.sh wake                       start every profile (needed after the TV fully restarts)
#   tools/profiles.sh remove 12                  delete profile 12 and everything signed in to it
# Then list people in launcher/local/values/people.xml ("Name|user id", "Guest|id|guest") and rebuild.
set -euo pipefail
cd "$(dirname "$0")/.."
PY="${PY:-tools/venv/bin/python}"
tv() { "$PY" -I tools/tv.py "$@"; }
case "${1:-}" in
  add)
    name="${2:?usage: tools/profiles.sh add <name> [debloat list]}"
    id=$(tv "pm create-user --profileOf 0 --user-type com.android.tv.profile $name" | sed -n 's/.*created user id \([0-9]*\).*/\1/p')
    [ -n "$id" ] || { echo "Couldn't create a profile. Is this a Google TV (Android 12+)?"; exit 1; }
    echo "Created profile $name as user $id"
    {
      echo "am start-user $id"
      # Your apps, plus Netflix (a system app on many TVs). Apps opened as SHARED (TV-only apps) stay in user 0.
      tv "pm list packages -3" | sed 's/package://' | tr -d '\r' | grep -v '^com.mihir.tvhome$' \
        | awk -v u="$id" '{print "pm install-existing --user " u " " $1}'
      echo "pm install-existing --user $id com.netflix.ninja"
      [ -n "${3:-}" ] && grep -vE '^\s*(#|$)' "$3" | awk -v u="$id" '{print "pm disable-user --user " u " " $1}'
      for p in com.google.android.youtube.tv com.google.android.apps.tv.launcherx com.google.android.tungsten.setupwraith; do
        echo "pm disable-user --user $id $p"
      done
    } | "$PY" -I tools/tv.py --batch | grep -iE 'error|fail' || true
    echo "Add \"$name|$id\" to launcher/local/values/people.xml, then launcher/build-and-install.sh" ;;
  list) tv "pm list users" ;;
  wake)
    # The people's profiles if listed, otherwise every profile.
    ids=$(sed -n 's/.*<item>[^|]*|\([0-9]*\).*/\1/p' launcher/local/values/people.xml 2>/dev/null || true)
    [ -n "$ids" ] || ids=$(tv "pm list users" | sed -n 's/.*UserInfo{\([0-9]*\):.*/\1/p')
    for id in $ids; do
      [ "$id" = 0 ] || tv "am start-user $id" | sed "s/^/user $id: /"
    done ;;
  remove) tv "pm remove-user ${2:?usage: tools/profiles.sh remove <user id>}" ;;
  *) sed -n '2,8p' "$0"; exit 1 ;;
esac
