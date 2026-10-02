import { DatePicker } from '@mui/x-date-pickers/DatePicker';
import { parseISO } from 'date-fns-jalali';
import { toIsoDate } from '../utils/format';

/** Jalali calendar picker that reads/writes Gregorian ISO dates (yyyy-MM-dd) for the API. */
export function JalaliDateField({ label, value, onChange, disablePast, required }: {
  label: string; value: string | null; onChange: (iso: string | null) => void; disablePast?: boolean; required?: boolean;
}) {
  return (
    <DatePicker
      label={label}
      value={value ? parseISO(value) : null}
      onChange={(d) => onChange(d && !Number.isNaN(d.getTime()) ? toIsoDate(d) : null)}
      disablePast={disablePast}
      slotProps={{ textField: { size: 'small', fullWidth: true, required } }}
    />
  );
}
