import { Button, Card, Table, TableBody, TableCell, TableContainer, TableHead, TableRow } from '@mui/material';
import { useQuery } from '@tanstack/react-query';
import { api, errorMessage } from '../../api/client';
import type { Invoice, Page } from '../../api/types';
import { Empty, Loading, PageHeader, StatusChip } from '../../components/common';
import { useNotify } from '../../components/Notify';
import { faDigits, formatDate, formatMoney } from '../../utils/format';
import { invoiceStatus } from '../../utils/labels';
import { payOnline } from './payOnline';

export default function MyInvoicesPage() {
  const notify = useNotify();
  const { data, isLoading } = useQuery({
    queryKey: ['my-invoices'],
    queryFn: () => api.get<Page<Invoice>>('/me/invoices', { params: { size: 50 } }).then((r) => r.data),
  });
  return (
    <>
      <PageHeader title="فاکتورهای من" />
      <Card>
        {isLoading ? <Loading /> : !data?.content.length ? <Empty /> : (
          <TableContainer>
            <Table size="small">
              <TableHead><TableRow>
                <TableCell>شماره</TableCell><TableCell>شرح</TableCell><TableCell>مبلغ</TableCell>
                <TableCell>تاریخ</TableCell><TableCell>وضعیت</TableCell><TableCell />
              </TableRow></TableHead>
              <TableBody>
                {data.content.map((i) => (
                  <TableRow key={i.id}>
                    <TableCell>{faDigits(i.number)}</TableCell>
                    <TableCell>{i.title}</TableCell>
                    <TableCell>{formatMoney(i.total)}</TableCell>
                    <TableCell>{formatDate(i.createdAt)}</TableCell>
                    <TableCell><StatusChip map={invoiceStatus} value={i.status} /></TableCell>
                    <TableCell>
                      {i.status === 'UNPAID' && (
                        <Button size="small" variant="contained"
                          onClick={() => payOnline(i.id).catch((e) => notify(errorMessage(e), 'error'))}>
                          پرداخت آنلاین
                        </Button>
                      )}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
        )}
      </Card>
    </>
  );
}
